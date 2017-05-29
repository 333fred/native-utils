package edu.wpi.first.nativeutils

import org.gradle.internal.os.OperatingSystem
import org.gradle.model.*
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.Tool
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.VisualCpp
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.api.GradleException
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.PlatformContainer

@Managed
interface BuildConfig {
    @SuppressWarnings("GroovyUnusedDeclaration")
    void setArchitecture(String arch)

    String getArchitecture()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setIsArm(boolean isArm)

    boolean getIsArm()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setOperatingSystem(String os)

    String getOperatingSystem()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setToolChainPrefix(String prefix)

    String getToolChainPrefix()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setCompilerArgs(List<String> args)

    List<String> getCompilerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setDebugCompilerArgs(List<String> args)

    List<String> getDebugCompilerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setDebugLinkerArgs(List<String> args)

    List<String> getDebugLinkerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setReleaseCompilerArgs(List<String> args)

    List<String> getReleaseCompilerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setReleaseLinkerArgs(List<String> args)

    List<String> getReleaseLinkerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setLinkerArgs(List<String> args)

    List<String> getLinkerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setCrossCompile(boolean cc)

    boolean getCrossCompile()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setCompilerFamily(String family)

    String getCompilerFamily()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setExclude(List<String> toExclude)

    List<String> getExclude()
}

interface BuildConfigSpec extends ModelMap<BuildConfig> {}

@SuppressWarnings("GroovyUnusedDeclaration")
class BuildConfigRules extends RuleSource {
    private static final ToolSearchPath toolSearchPath;

    static {
        toolSearchPath = new ToolSearchPath(OperatingSystem.current())
    }

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
    void setTargetPlatforms(ComponentSpecContainer components, BuildConfigSpec configs) {
        components.each { component ->
            configs.findAll { isConfigEnabled(it) }.each { config ->
                if (config.exclude == null || !config.exclude.contains(component.name)) {
                    component.targetPlatform config.architecture
                }
            }
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void addBuildTypes(BuildTypeContainer buildTypes) {
        ['debug', 'release'].each {
            buildTypes.create(it)
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
    void createPlatforms(PlatformContainer platforms, BuildConfigSpec configs) {
        if (configs == null) {
            return
        }

        configs.findAll { isConfigEnabled(it) }.each { config ->
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
    void setDebugToolChainArgs(BinaryContainer binaries, BuildConfigSpec configs) {
        if (configs == null) {
            return
        }

        def enabledConfigs = configs.findAll {
            isConfigEnabled(it) && (it.debugCompilerArgs != null || it.debugLinkerArgs != null)
        }
        if (enabledConfigs == null || enabledConfigs.empty) {
            return
        }

        binaries.findAll { it.buildType.name == 'debug' }.each { binary ->
            println binary
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
    void setReleaseToolChainArgs(BinaryContainer binaries, BuildConfigSpec configs) {
        if (configs == null) {
            return
        }

        def enabledConfigs = configs.findAll {
            isConfigEnabled(it) && (it.releaseCompilerArgs != null || it.releaseLinkerArgs != null)
        }
        if (enabledConfigs == null || enabledConfigs.empty) {
            return
        }

        binaries.findAll { it.buildType.name == 'release' }.each { binary ->
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

    @SuppressWarnings("GroovyUnusedDeclaration")
    @Mutate
    void createToolChains(NativeToolChainRegistry toolChains, BuildConfigSpec configs) {
        if (configs == null) {
            return
        }

        def vcppConfigs = configs.findAll { isConfigEnabled(it) && it.compilerFamily == 'VisualCpp' }
        if (vcppConfigs != null && !vcppConfigs.empty) {
            toolChains.create('visualCpp', VisualCpp.class) { t ->
                t.eachPlatform { toolChain ->
                    def config = vcppConfigs.find { it.architecture == toolChain.platform.architecture.name }
                    if (config != null) {
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

        def gccConfigs = configs.findAll { isConfigEnabled(it) && it.compilerFamily == 'Gcc' }
        if (gccConfigs != null && !gccConfigs.empty) {
            toolChains.create('gcc', Gcc.class) {
                gccConfigs.each { config ->
                    target(config.architecture) {
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

        def clangConfigs = configs.findAll { isConfigEnabled(it) && it.compilerFamily == 'Clang' }
        if (clangConfigs != null && !clangConfigs.empty) {
            toolChains.create('clang', Clang.class) {
                clangConfigs.each { config ->
                    target(config.architecture) {
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

    /**
     * If a config is crosscompiling, only enable for athena. Otherwise, only enable if the current os is the config os,
     * or specific cross compiler is specified
     */
    private boolean isConfigEnabled(BuildConfig config) {
        if (config.crossCompile) {
            return doesToolChainExist(config)
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

    private boolean doesToolChainExist(BuildConfig config) {
        if (!config.crossCompile) {
            return true;
        }

        return toolSearchPath.locate(ToolType.CPP_COMPILER, config.toolChainPrefix + "g++").isAvailable();
    }
}