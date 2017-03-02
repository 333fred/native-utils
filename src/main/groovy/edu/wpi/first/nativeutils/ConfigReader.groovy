package edu.wpi.first.nativeutils

import org.gradle.internal.os.OperatingSystem
import org.gradle.model.*
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.VisualCpp
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.PlatformContainer

@Managed interface BuildConfig {
    @SuppressWarnings("GroovyUnusedDeclaration")
    void setArchitecture(String arch)
    String getArchitecture()

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
    @SuppressWarnings("GroovyUnusedDeclaration")
    @Model('buildConfigs') void createBuildConfigs(BuildConfigSpec configs) {}

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Validate void validateCompilerFamilyExists(BuildConfigSpec configs) {
        configs.each { config ->
            assert config.compilerFamily == 'VisualCpp' ||
                   config.compilerFamily == 'Gcc' ||
                   config.compilerFamily == 'Clang'
        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    @Validate void validateOsExists(BuildConfigSpec configs) {
        def validOs = ['windows', 'osx', 'linux', 'unix']
        configs.each { config ->
            assert validOs.contains(config.operatingSystem.toLowerCase())
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Validate void setTargetPlatforms(ComponentSpecContainer components, BuildConfigSpec configs) {
        components.each { component ->
            configs.findAll { isConfigEnabled(it) }.each { config ->
                if (config.exclude == null || !config.exclude.contains(component.name)) {
                    component.targetPlatform config.architecture
                }
            }
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate void disableCrossCompileGoogleTest(BinaryContainer binaries, BuildConfigSpec configs) {
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
    @Mutate void createPlatforms(PlatformContainer platforms, BuildConfigSpec configs) {
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

    @SuppressWarnings("GroovyUnusedDeclaration")
    @Mutate void createToolChains(NativeToolChainRegistry toolChains, BuildConfigSpec configs) {
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

    /**
     * If a config is crosscompiling, always enable. Otherwise, only enable if the current os is the config os.
     */
    private boolean isConfigEnabled(BuildConfig config) {
        if (config.crossCompile) {
            return true
        }

        def currentOs;

        if (OperatingSystem.current().isWindows()) {
            currentOs = 'windows'
        } else if (OperatingSystem.current().isMacOsX()) {
            currentOs = 'osx'
        } else if (OperatingSystem.current().isLinux()) {
            currentOs = 'linux'
        } else if (OperatingSystem.current().isUnix()) {
            currentOs = 'unix'
        }

        return currentOs == config.operatingSystem.toLowerCase()
    }
}