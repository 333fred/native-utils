package edu.wpi.first.nativeutils.rules

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.base.internal.ProjectLayout
import org.gradle.model.*
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.Tool
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.VisualCpp
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.PlatformContainer
import edu.wpi.first.nativeutils.configs.BuildConfig
import edu.wpi.first.nativeutils.NativeUtils
import edu.wpi.first.nativeutils.configs.CrossBuildConfig

class BuildConfigRulesBase  {
    private static final ToolSearchPath toolSearchPath = new ToolSearchPath(OperatingSystem.current())

    static String binTools(String tool, ProjectLayout projectLayout, BuildConfig config) {
        def toolChainPath = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)
        def compilerPrefix = config.toolChainPrefix
        if (toolChainPath != null) return "${toolChainPath}/${compilerPrefix}${tool}"
        return "${compilerPrefix}${tool}"
    }

    static void addArgsToTool(Tool tool, args) {
        if (args != null) {
            tool.args.addAll((List<String>)args)
        }
    }

    static Class getCompilerFamily(String family) {
        switch (family) {
            case 'VisualCpp':
                return VisualCpp
            case 'Gcc':
                return Gcc
            case 'Clang':
                return Clang
        }
    }

    static boolean isNativeProject(BinarySpec binary) {
        return binary instanceof NativeBinarySpec
    }

    static boolean isCrossCompile(BuildConfig config) {
        return config in CrossBuildConfig
    }

    static boolean isComponentEnabled(BuildConfig config, String componentName) {
        if (config.exclude == null || config.exclude.size() == 0) {
            return true
        }
        return !config.exclude.contains(componentName)
    }

    /**
     * If a config is crosscompiling, only enable for athena. Otherwise, only enable if the current os is the config os,
     * or specific cross compiler is specified
     */
    static boolean isConfigEnabled(BuildConfig config, ProjectLayout projectLayout) {
        if (isCrossCompile(config)) {
            return doesToolChainExist(config, projectLayout)
        }
        if (!config.detectPlatform) {
            return false
        }

        return config.detectPlatform(config)
    }

    static boolean doesToolChainExist(BuildConfig config, ProjectLayout projectLayout) {
        if (!isCrossCompile(config)) {
            return true;
        }

        def path = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)

        def toolPath = path == null ? "" : path

        return toolSearchPath.locate(ToolType.CPP_COMPILER, toolPath + config.toolChainPrefix + "g++").isAvailable();
    }
}