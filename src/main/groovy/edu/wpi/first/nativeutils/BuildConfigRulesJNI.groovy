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
import org.gradle.api.file.FileTree

@SuppressWarnings("GroovyUnusedDeclaration")
class BuildConfigRulesJNI extends RuleSource {

    @SuppressWarnings("GroovyUnusedDeclaration")
    @Model('jniConfig')
    void createJniConfig(JNIConfig config) {}

    @Mutate
    void createJniTasks(ModelMap<Task> tasks, JNIConfig jniConfig, ProjectLayout projectLayout,
                        BinaryContainer binaries, BuildTypeContainer buildTypes, BuildConfigSpec configs) {
        def project = projectLayout.projectIdentifier
        def generatedJNIHeaderLoc = "${project.buildDir}/jniinclude"

        tasks.create('jniHeaders') {
            def outputFolder = project.file(generatedJNIHeaderLoc)
            inputs.files project.sourceSets.main.output
            outputs.file outputFolder
            doLast {
                outputFolder.mkdirs()
                project.exec {
                    executable org.gradle.internal.jvm.Jvm.current().getExecutable('javah')
                    args '-d', outputFolder
                    args '-classpath', project.sourceSets.main.output.classesDir
                    jniConfig.jniDefinitionClasses.each {
                        args it
                    }
                }
            }
        }

        def jniHeaders = tasks.get('jniHeaders')

        def getJniSymbols = {
            def symbolsList = []

            jniHeaders.outputs.files.each {
                FileTree tree = project.fileTree(dir: it)
                tree.each { File file ->
                    file.eachLine { line ->
                        if (line.trim()) {
                            if (line.startsWith("JNIEXPORT ") && line.contains('JNICALL')) {
                                def (p1, p2) = line.split('JNICALL').collect { it.trim() }
                                // p2 is our JNI call
                                symbolsList << p2
                            }
                        }
                    }
                }
            }

            return symbolsList
        }

        configs.findAll { BuildConfigRulesBase.isConfigEnabled(it, projectLayout) }.each { config ->
            binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }.each { binary ->
                if (binary.targetPlatform.architecture.name == config.architecture
                    && binary.targetPlatform.operatingSystem.name == config.operatingSystem
                    && binary.targetPlatform.operatingSystem.name != 'windows'
                    && binary instanceof SharedLibraryBinarySpec) {
                    def input = binary.buildTask.name
                    def linkTaskName = 'link' + input.substring(0, 1).toUpperCase() + input.substring(1);
                    def task = tasks.get(linkTaskName)
                    task.doLast {
                        def library = task.outputFile.absolutePath
                        def nmOutput = "${BuildConfigRulesBase.binTools('nm', projectLayout, config)} ${library}".execute().text

                        def nmSymbols = nmOutput.toString().replace('\r', '')

                        def symbolList = getJniSymbols()

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

        configs.findAll { BuildConfigRulesBase.isConfigEnabled(it, projectLayout) }.each { config ->
            binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }.each { binary ->
                if (binary.targetPlatform.architecture.name == config.architecture
                && binary.targetPlatform.operatingSystem.name == config.operatingSystem ) {
                    if (config.crossCompile) {
                        binary.cppCompiler.args '-I', jniConfig.jniArmHeaderLocation.absolutePath
                        binary.cppCompiler.args '-I', jniConfig.jniArmHeaderLocation.absolutePath + '/linux'
                    } else {
                        def jdkLocation = org.gradle.internal.jvm.Jvm.current().javaHome
                        NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include", binary.cppCompiler)
                        if (binary.targetPlatform.operatingSystem.macOsX) {
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/darwin", binary.cppCompiler)
                        } else if (binary.targetPlatform.operatingSystem.linux) {
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/linux", binary.cppCompiler)
                        } else if (binary.targetPlatform.operatingSystem.windows) {
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/win32", binary.cppCompiler)
                        } else if (binary.targetPlatform.operatingSystem.freeBSD) {
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/freebsd", binary.cppCompiler)
                        } else if (file("$jdkLocation/include/darwin").exists()) {
                            // TODO: As of Gradle 2.8, targetPlatform.operatingSystem.macOsX returns false
                            // on El Capitan. We therefore manually test for the darwin folder and include it
                            // if it exists
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/darwin", binary.cppCompiler)
                        }
                    }
                    jniHeaders.outputs.files.each { file ->
                        if (config.crossCompile) {
                            binary.cppCompiler.args '-I', file.getPath()
                        } else {
                            NativeUtils.setPlatformSpecificIncludeFlag(file.getPath(), binary.cppCompiler)
                        }
                    }

                    binary.buildTask.dependsOn jniHeaders
                }
            }
         }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void createJniZipTasks(ModelMap<Task> tasks, BinaryContainer binaries, BuildTypeContainer buildTypes, 
                        ProjectLayout projectLayout, BuildConfigSpec configs, JNIConfig jniConfig) {

        def project = projectLayout.projectIdentifier
        buildTypes.each { buildType ->
            configs.findAll { BuildConfigRulesBase.isConfigEnabled(it, projectLayout) }.each { config ->
                def taskName = 'zip' + config.operatingSystem + config.architecture + buildType.name
                def jniTaskName = taskName + 'jni'
                tasks.create(jniTaskName, Jar) { task ->
                    description = 'Creates jni jar of the libraries'
                    destinationDir =  projectLayout.buildDir
                    classifier = config.operatingSystem + config.architecture
                    baseName = 'jni'
                    duplicatesStrategy = 'exclude'

                    binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }.each { binary ->
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
                def buildTask = tasks.get('build')
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