package edu.wpi.first.nativeutils

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.Copy
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.base.internal.ProjectLayout
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.model.*
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec
import org.gradle.nativeplatform.Tool
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.VisualCpp
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.PlatformContainer

interface BuildConfigSpec extends ModelMap<BuildConfig> {}

@SuppressWarnings("GroovyUnusedDeclaration")
class BuildConfigRules extends RuleSource {
    private static final ToolSearchPath toolSearchPath = new ToolSearchPath(OperatingSystem.current())

    @SuppressWarnings("GroovyUnusedDeclaration")
    @Model('buildConfigs')
    void createBuildConfigs(BuildConfigSpec configs) {}

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Validate
    void validateCompilerFamilyExists(BuildConfigSpec configs) {
        configs.each { config ->
            assert config.compilerFamily == 'VisualCpp' ||
                    config.compilerFamily == 'Gcc' ||
                    config.compilerFamily == 'Clang'
        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    @Validate
    void validateOsExists(BuildConfigSpec configs) {
        def validOs = ['windows', 'osx', 'linux', 'unix']
        configs.each { config ->
            assert validOs.contains(config.operatingSystem.toLowerCase())
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Validate
    void setTargetPlatforms(ComponentSpecContainer components, ProjectLayout projectLayout, BuildConfigSpec configs) {
        components.each { component ->
            configs.findAll { isConfigEnabled(it, projectLayout) }.each { config ->
                if (config.exclude == null || !config.exclude.contains(component.name)) {
                    component.targetPlatform config.architecture
                }
            }
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void addBuildTypes(BuildTypeContainer buildTypes, ProjectLayout projectLayout) {
        if (projectLayout.projectIdentifier.hasProperty('releaseBuild')) {
            buildTypes.create('release')
        } else {
            buildTypes.create('debug')
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void disableCrossCompileGoogleTest(BinaryContainer binaries, BuildConfigSpec configs) {
        def crossCompileConfigs = configs.findAll { it.crossCompile }.collect { it.architecture }
        if (crossCompileConfigs != null && !crossCompileConfigs.empty) {
            binaries.withType(GoogleTestTestSuiteBinarySpec) { spec ->
                if (crossCompileConfigs.contains(spec.targetPlatform.architecture.name)) {
                    spec.buildable = false
                }
            }
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void setSkipGoogleTest(BinaryContainer binaries, ProjectLayout projectLayout, BuildConfigSpec configs) {
        def skipAllTests = projectLayout.projectIdentifier.hasProperty('skipAllTests')
        def skipConfigs = configs.findAll { it.skipTests }.collect { it.architecture }
        if (skipConfigs != null && !skipConfigs.empty) {
            binaries.withType(GoogleTestTestSuiteBinarySpec) { spec ->
                if (skipConfigs.contains(spec.targetPlatform.architecture.name) || skipAllTests) {
                    spec.buildable = false
                }
            }
        }
    }

    private String binTools(String tool, ProjectLayout projectLayout, BuildConfig config) {
        def toolChainPath = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)
        def compilerPrefix = config.toolChainPrefix
        if (toolChainPath != null) return "${toolChainPath}/${compilerPrefix}${tool}"
        return "${compilerPrefix}${tool}"
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void createJniCheckTasks(ModelMap<Task> tasks, BinaryContainer binaries, BuildTypeContainer buildTypes, 
                        ProjectLayout projectLayout, BuildConfigSpec configs) {
        // Only create JNI check tasks if not the gmock project, and if getJniSymbols is extension
        // defined in the project
        if (projectLayout.projectIdentifier.hasProperty('gmockProject')) {
            return
        }
        def jniSymbolFunc = projectLayout.projectIdentifier.findProperty('getJniSymbols')
        if (jniSymbolFunc == null) {
            return;
        }
        configs.findAll { isConfigEnabled(it, projectLayout) }.each { config ->
            binaries.findAll { isNativeProject(it) }.each { binary ->
                if (binary.targetPlatform.architecture.name == config.architecture
                    && binary.targetPlatform.operatingSystem.name == config.operatingSystem
                    && binary.targetPlatform.operatingSystem.name != 'windows'
                    && binary instanceof SharedLibraryBinarySpec) {
                    def input = binary.buildTask.name
                    def linkTaskName = 'link' + input.substring(0, 1).toUpperCase() + input.substring(1);
                    def task = tasks.get(linkTaskName)
                    task.doLast {
                        def library = task.outputFile.absolutePath
                        def nmOutput = "${binTools('nm', config)} ${library}".execute().text

                        def nmSymbols = nmOutput.toString().replace('\r', '')

                        def symbolList = jniSymbolFunc()

                        symbolList.each {
                            //Add \n so we can check for the exact symbol
                            def found = nmSymbols.contains(it + '\n')
                            if (!found) {
                                throw new GradleException("Found a definition that does not have a matching symbol ${it}")
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void createStripTasks(ModelMap<Task> tasks, BinaryContainer binaries, ProjectLayout projectLayout, BuildConfigSpec configs) {
        // Only strip binaries if not a gmock project, if not windows, and if release
        if (projectLayout.projectIdentifier.hasProperty('gmockProject')) {
            return
        }
        configs.findAll { isConfigEnabled(it, projectLayout) }.each { config ->
            binaries.findAll { isNativeProject(it) }.each { binary ->
                if (binary.targetPlatform.architecture.name == config.architecture
                    && binary.targetPlatform.operatingSystem.name == config.operatingSystem 
                    && binary.buildType.name == 'release' 
                    && binary.targetPlatform.operatingSystem.name != 'windows'
                    && binary instanceof SharedLibraryBinarySpec) {
                    def input = binary.buildTask.name
                    def linkTaskName = 'link' + input.substring(0, 1).toUpperCase() + input.substring(1);
                    def task = tasks.get(linkTaskName)
                    if (binary.targetPlatform.operatingSystem.name == 'osx') {
                        def library = task.outputFile.absolutePath
                        task.doLast {
                            "dsymutil ${library}".execute()
                            "strip -S ${library}".execute()
                        }
                    } else {
                        def library = task.outputFile.absolutePath
                        def debugLibrary = task.outputFile.absolutePath + ".debug"
                        task.doLast {
                            "${binTools('objcopy', projectLayout, config)} --only-keep-debug ${library} ${debugLibrary}".execute()
                            "${binTools('strip', projectLayout, config)} -g ${library}".execute()
                            "${binTools('objcopy', projectLayout, config)} --add-gnu-debuglink=${debugLibrary} ${library}".execute()
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void createDependencies(ModelMap<Task> tasks, BinaryContainer binaries,
                        ProjectLayout projectLayout, BuildConfigSpec configs) {
        // Only create dependencies if not gmock project
        if (projectLayout.projectIdentifier.hasProperty('gmockProject')) {
            return
        }                   
        def rootProject = projectLayout.projectIdentifier.rootProject
        def currentProject = projectLayout.projectIdentifier

        currentProject.configurations.create('nativeDeps')

        def createdHeaders = []

        configs.findAll { isConfigEnabled(it, projectLayout) }.each { config ->
            currentProject.dependencies {
                config.sharedDeps.each { dep ->
                    if (!createdHeaders.contains(dep)) {
                        createdHeaders.add(dep)
                        nativeDeps "${dep}-cpp:+:headers@zip"
                    }
                    nativeDeps "${dep}-cpp:+:${NativeUtils.getClassifier(config)}@zip"
                }
                config.staticDeps.each { dep ->
                    if (!createdHeaders.contains(dep)) {
                        createdHeaders.add(dep)
                        nativeDeps "${dep}-cpp:+:headers@zip"
                    }
                    nativeDeps "${dep}-cpp:+:${NativeUtils.getClassifier(config)}@zip"
                }
            }
        }

        def depLocation = "${rootProject.buildDir}/dependencies"

        currentProject.configurations.nativeDeps.files.each { file->
            def depName = file.name.substring(0, file.name.indexOf('-'))
            def fileWithoutZip = file.name.take(file.name.lastIndexOf('.') + 1)
            if (fileWithoutZip.endsWith('headers.')) {
                def headerTaskName = "download${depName}Headers"
                def headerTask = rootProject.tasks.findByPath(headerTaskName)
                if (headerTask == null) {
                    headerTask = rootProject.tasks.create(headerTaskName , Copy) {
                        description = "Downloads and unzips the $depName header dependency."
                        group = 'Dependencies'
                        from rootProject.zipTree(file)
                        into "$depLocation/${depName.toLowerCase()}/headers"
                    }
                    binaries.findAll { isNativeProject(it) }.each { binary ->
                        binary.buildTask.dependsOn headerTask
                    }
                }
            } else {
                // Non headers task
                configs.findAll { isConfigEnabled(it, projectLayout) && fileWithoutZip.endsWith("${NativeUtils.getClassifier(it)}.")}.each { config ->
                    def classifier = NativeUtils.getClassifier(config)
                    def downloadTaskName = "download${depName}${classifier}"
                    def downloadTask = rootProject.tasks.findByPath(downloadTaskName)
                    if (downloadTask == null) {
                        downloadTask = rootProject.tasks.create(downloadTaskName , Copy) {
                            description = "Downloads and unzips the $depName $classifier dependency."
                            group = 'Dependencies'
                            from rootProject.zipTree(file)
                            into "$depLocation/${depName.toLowerCase()}/${classifier}"
                        }
                        binaries.findAll { isNativeProject(it) }.each { binary ->
                            if (binary.targetPlatform.architecture.name == config.architecture
                            && binary.targetPlatform.operatingSystem.name == config.operatingSystem ) {
                                binary.buildTask.dependsOn downloadTask
                            }
                        }
                    }
                }
            }            
        }

        configs.findAll { isConfigEnabled(it, projectLayout) }.each { config ->
            binaries.findAll { isNativeProject(it) }.each { binary ->
                if (binary.targetPlatform.architecture.name == config.architecture
                && binary.targetPlatform.operatingSystem.name == config.operatingSystem ) {
                    config.sharedDeps.each { dep ->
                        def depName = dep.split(':', 2)[1]
                        binary.lib(new WPILibDependencySet("$depLocation/${depName.toLowerCase()}", config, depName, currentProject, true))
                    }
                    config.staticDeps.each { dep ->
                        def depName = dep.split(':', 2)[1]
                        binary.lib(new WPILibDependencySet("$depLocation/${depName.toLowerCase()}", config, depName, currentProject, false))
                    }
                }
            }
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void createZipTasks(ModelMap<Task> tasks, BinaryContainer binaries, BuildTypeContainer buildTypes, 
                        ProjectLayout projectLayout, BuildConfigSpec configs) {
        // Only create zip tasks if not gmock project
        if (projectLayout.projectIdentifier.hasProperty('gmockProject')) {
            return
        }

        def project = projectLayout.projectIdentifier
        buildTypes.each { buildType ->
            configs.findAll { isConfigEnabled(it, projectLayout) }.each { config ->
                def taskName = 'zip' + config.operatingSystem + config.architecture + buildType.name
                tasks.create(taskName, Zip) { task ->
                    description = 'Creates platform zip of the libraries'
                    destinationDir =  projectLayout.buildDir
                    classifier = config.operatingSystem + config.architecture
                    baseName = 'zip'
                    duplicatesStrategy = 'exclude'

                    binaries.findAll { isNativeProject(it) }.each { binary ->
                        if (binary.targetPlatform.architecture.name == config.architecture
                            && binary.targetPlatform.operatingSystem.name == config.operatingSystem 
                            && binary.buildType.name == buildType.name) {
                            if (binary instanceof SharedLibraryBinarySpec) {
                                dependsOn binary.buildTask
                                from(new File(binary.sharedLibraryFile.absolutePath + ".debug")) {
                                    into NativeUtils.getPlatformPath(config) + '/shared'
                                }
                                from (binary.sharedLibraryFile) {
                                    into NativeUtils.getPlatformPath(config) + '/shared'
                                }
                            } else if (binary instanceof StaticLibraryBinarySpec) {
                                dependsOn binary.buildTask
                                from (binary.staticLibraryFile) {
                                    into NativeUtils.getPlatformPath(config) + '/static'
                                }
                            }
                        }
                    }
                }

                def buildTask = tasks.get('build')
                def zipTask = tasks.get(taskName)
                buildTask.dependsOn zipTask

                project.artifacts {
                    zipTask
                }

                project.publishing.publications {
                    it.each { publication->
                        if (publication.name == 'cpp') {
                            zipTask.outputs.files.each { file ->
                                if (!publication.artifacts.contains(file))
                                {
                                    publication.artifact zipTask 
                                }
                            }
                        }
                    }
                }


                def jniSymbolFunc = projectLayout.projectIdentifier.findProperty('getJniSymbols')
                if (jniSymbolFunc != null) {
                    def jniTaskName = taskName + 'jni'

                    tasks.create(jniTaskName, Jar) { task ->
                        description = 'Creates jni jar of the libraries'
                        destinationDir =  projectLayout.buildDir
                        classifier = config.operatingSystem + config.architecture
                        baseName = 'jni'
                        duplicatesStrategy = 'exclude'

                        binaries.findAll { isNativeProject(it) }.each { binary ->
                            if (binary.targetPlatform.architecture.name == config.architecture
                                && binary.targetPlatform.operatingSystem.name == config.operatingSystem 
                                && binary.buildType.name == buildType.name) {
                                if (binary instanceof SharedLibraryBinarySpec) {
                                    dependsOn binary.buildTask
                                    from (binary.sharedLibraryFile) {
                                        into NativeUtils.getPlatformPath(config)
                                    }
                                }
                            }
                        }
                    }
                    def jniTask = tasks.get(jniTaskName)
                    buildTask.dependsOn jniTask
                    project.artifacts {
                        jniTask
                    }

                    project.publishing.publications {
                        it.each { publication->
                            if (publication.name == 'jni') {
                                jniTask.outputs.files.each { file ->
                                    if (!publication.artifacts.contains(file))
                                    {
                                        publication.artifact jniTask
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
    }


    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void createPlatforms(PlatformContainer platforms, ProjectLayout projectLayout, BuildConfigSpec configs) { 
        if (configs == null) {
            return
        }

        configs.findAll { isConfigEnabled(it, projectLayout) }.each { config ->
            if (config.architecture != null) {
                platforms.create(config.architecture) { platform ->
                    platform.architecture config.architecture
                    if (config.operatingSystem != null) {
                        platform.operatingSystem config.operatingSystem
                    }
                }
            }
        }
    }

    @Validate
    void setDebugToolChainArgs(BinaryContainer binaries, ProjectLayout projectLayout, BuildConfigSpec configs) {
        if (configs == null) {
            return
        }

        def enabledConfigs = configs.findAll {
            isConfigEnabled(it, projectLayout) && (it.debugCompilerArgs != null || it.debugLinkerArgs != null)
        }
        if (enabledConfigs == null || enabledConfigs.empty) {
            return
        }

        binaries.findAll { 
            isNativeProject(it) && it.buildType.name == 'debug' }.each { binary ->
            def config = enabledConfigs.find {
                it.architecture == binary.targetPlatform.architecture.name &&
                        getCompilerFamily(it.compilerFamily).isAssignableFrom(binary.toolChain.class)
            }
            if (config != null) {
                addArgsToTool(binary.cppCompiler, config.debugCompilerArgs)
                addArgsToTool(binary.linker, config.debugLinkerArgs)
            }
        }
    }

    @Validate
    void setReleaseToolChainArgs(BinaryContainer binaries, ProjectLayout projectLayout, BuildConfigSpec configs) {
        if (configs == null) {
            return
        }

        def enabledConfigs = configs.findAll {
            isConfigEnabled(it, projectLayout) && (it.releaseCompilerArgs != null || it.releaseLinkerArgs != null)
        }
        if (enabledConfigs == null || enabledConfigs.empty) {
            return
        }

        binaries.findAll { 
            isNativeProject(it) && it.buildType.name == 'release' }.each { binary ->
            def config = enabledConfigs.find {
                it.architecture == binary.targetPlatform.architecture.name &&
                        getCompilerFamily(it.compilerFamily).isAssignableFrom(binary.toolChain.class)
            }
            if (config != null) {
                addArgsToTool(binary.cppCompiler, config.releaseCompilerArgs)
                addArgsToTool(binary.linker, config.releaseLinkerArgs)
            }
        }
    }

    @Validate
    void setDefFileToolChainArgs(BinaryContainer binaries, ProjectLayout projectLayout, BuildConfigSpec configs) {
        if (configs == null) {
            return
        }

        def enabledConfigs = configs.findAll {
            isConfigEnabled(it, projectLayout) && (it.defFile != null)
        }
        if (enabledConfigs == null || enabledConfigs.empty) {
            return
        }

        binaries.findAll { 
            isNativeProject(it) }.each { binary ->
            def config = enabledConfigs.find {
                it.operatingSystem == 'windows' &&
                        getCompilerFamily(it.compilerFamily).isAssignableFrom(binary.toolChain.class)
            }
            if (config != null) {
                addArgsToTool(binary.linker, "/DEF:${config.defFile}")
            }
        }
    }

    @Validate
    void storeAllBuildConfigs(BuildConfigSpec configs, ProjectLayout projectLayout) {
        configs.findAll { isConfigEnabled(it, projectLayout) }.each {
            NativeUtils.buildConfigs.add(it)
        }
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @Mutate
    void createToolChains(NativeToolChainRegistry toolChains, ProjectLayout projectLayout, BuildConfigSpec configs) {
        if (configs == null) {
            return
        }

        def vcppConfigs = configs.findAll { isConfigEnabled(it, projectLayout) && it.compilerFamily == 'VisualCpp' }
        if (vcppConfigs != null && !vcppConfigs.empty) {
            toolChains.create('visualCpp', VisualCpp.class) { t ->
                t.eachPlatform { toolChain ->
                    def config = vcppConfigs.find { it.architecture == toolChain.platform.architecture.name }
                    if (config != null) {
                        def vsToolPath = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)
                        if (vsToolPath != null) {
                            path(vsToolPath)
                        }
                        if (config.toolChainPrefix != null) {
                            toolChain.cCompiler.executable = config.toolChainPrefix + toolChain.cCompiler.executable
                            toolChain.cppCompiler.executable = config.toolChainPrefix + toolChain.cppCompiler.executable
                            toolChain.linker.executable = config.toolChainPrefix + toolChain.linker.executable
                            toolChain.assembler.executable = config.toolChainPrefix + toolChain.assembler.executable
                            toolChain.staticLibArchiver.executable = config.toolChainPrefix + toolChain.staticLibArchiver.executable
                        }

                        if (config.compilerArgs != null) {
                            toolChain.cppCompiler.withArguments { args ->
                                config.compilerArgs.each { a -> args.add(a) }
                            }
                        }
                        if (config.linkerArgs != null) {
                            toolChain.linker.withArguments { args ->
                                config.linkerArgs.each { a -> args.add(a) }
                            }
                        }
                    }
                }
            }
        }

        def gccConfigs = configs.findAll { isConfigEnabled(it, projectLayout) && it.compilerFamily == 'Gcc' }
        if (gccConfigs != null && !gccConfigs.empty) {
            toolChains.create('gcc', Gcc.class) {
                gccConfigs.each { config ->
                    target(config.architecture) {
                        def gccToolPath = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)
                        if (gccToolPath != null) {
                            path(gccToolPath)
                        }
                        if (config.toolChainPrefix != null) {
                            cCompiler.executable = config.toolChainPrefix + cCompiler.executable
                            cppCompiler.executable = config.toolChainPrefix + cppCompiler.executable
                            linker.executable = config.toolChainPrefix + linker.executable
                            assembler.executable = config.toolChainPrefix + assembler.executable
                            staticLibArchiver.executable = config.toolChainPrefix + staticLibArchiver.executable
                        }

                        if (config.compilerArgs != null) {
                            cppCompiler.withArguments { args ->
                                config.compilerArgs.each { a -> args.add(a) }
                            }
                        }
                        if (config.linkerArgs != null) {
                            linker.withArguments { args ->
                                config.linkerArgs.each { a -> args.add(a) }
                            }
                        }
                    }
                }
            }
        }

        def clangConfigs = configs.findAll { isConfigEnabled(it, projectLayout) && it.compilerFamily == 'Clang' }
        if (clangConfigs != null && !clangConfigs.empty) {
            toolChains.create('clang', Clang.class) {
                clangConfigs.each { config ->
                    target(config.architecture) {
                        def clangToolPath = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)
                        if (clangToolPath != null) {
                            path(clangToolPath)
                        }
                        if (config.toolChainPrefix != null) {
                            cCompiler.executable = config.toolChainPrefix + cCompiler.executable
                            cppCompiler.executable = config.toolChainPrefix + cppCompiler.executable
                            linker.executable = config.toolChainPrefix + linker.executable
                            assembler.executable = config.toolChainPrefix + assembler.executable
                            staticLibArchiver.executable = config.toolChainPrefix + staticLibArchiver.executable
                        }

                        if (config.compilerArgs != null) {
                            cppCompiler.withArguments { args ->
                                config.compilerArgs.each { a -> args.add(a) }
                            }
                        }
                        if (config.linkerArgs != null) {
                            linker.withArguments { args ->
                                config.linkerArgs.each { a -> args.add(a) }
                            }
                        }
                    }
                }
            }
        }
    }

    private void addArgsToTool(Tool tool, args) {
        if (args != null) {
            tool.args.addAll((List<String>)args)
        }
    }

    private Class getCompilerFamily(String family) {
        switch (family) {
            case 'VisualCpp':
                return VisualCpp
            case 'Gcc':
                return Gcc
            case 'Clang':
                return Clang
        }
    }

    private boolean isNativeProject(BinarySpec binary) {
        return binary instanceof NativeBinarySpec
    }

    /**
     * If a config is crosscompiling, only enable for athena. Otherwise, only enable if the current os is the config os,
     * or specific cross compiler is specified
     */
    private boolean isConfigEnabled(BuildConfig config, ProjectLayout projectLayout) {
        if (config.crossCompile) {
            return doesToolChainExist(config, projectLayout)
        }

        def currentOs;
        def isArm;

        if (OperatingSystem.current().isWindows()) {
            currentOs = 'windows'
            isArm = false
        } else if (OperatingSystem.current().isMacOsX()) {
            currentOs = 'osx'
            isArm = false
        } else if (OperatingSystem.current().isLinux()) {
            currentOs = 'linux'
            def arch = System.getProperty("os.arch")
            if (arch == 'amd64' || arch == 'i386') {
                isArm = false
            } else {
                isArm = true
            }
        } else if (OperatingSystem.current().isUnix()) {
            currentOs = 'unix'
            def arch = System.getProperty("os.arch")
            if (arch == 'amd64' || arch == 'i386') {
                isArm = false
            } else {
                isArm = true
            }
        }



        return currentOs == config.operatingSystem.toLowerCase() &&
               isArm == config.isArm
    }

    private boolean doesToolChainExist(BuildConfig config, ProjectLayout projectLayout) {
        if (!config.crossCompile) {
            return true;
        }

        def path = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)

        def toolPath = path == null ? "" : path

        return toolSearchPath.locate(ToolType.CPP_COMPILER, toolPath + config.toolChainPrefix + "g++").isAvailable();
    }
}