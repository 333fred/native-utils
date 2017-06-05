package edu.wpi.first.nativeutils

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

    static boolean doesToolChainExist(BuildConfig config, ProjectLayout projectLayout) {
        if (!config.crossCompile) {
            return true;
        }

        def path = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)

        def toolPath = path == null ? "" : path

        return toolSearchPath.locate(ToolType.CPP_COMPILER, toolPath + config.toolChainPrefix + "g++").isAvailable();
    }
}