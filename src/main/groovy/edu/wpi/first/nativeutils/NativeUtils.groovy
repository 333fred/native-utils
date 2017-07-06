package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.Tool
import edu.wpi.first.nativeutils.configs.BuildConfig
import edu.wpi.first.nativeutils.rules.BuildConfigRulesBase
import edu.wpi.first.nativeutils.configs.CrossBuildConfig

/**
 * Created by 333fr on 3/1/2017.
 */
public class NativeUtils implements Plugin<Project> {
    private static final Map<BuildConfig, String> toolChainPathCache = [:]

    /**
     * Gets the toolChainPath for the specific build configuration
     */
    public static String getToolChainPath(BuildConfig config, Project project) {
        if (!BuildConfigRulesBase.isCrossCompile(config)) {
            return null
        }

        if (toolChainPathCache.containsKey(config)) {
            return toolChainPathCache.get(config)
        }
        for (item in project.properties) {
            def key = item.key
            def value = item.value
            if (key.contains('-toolChainPath')) {
                String[] configSplit = key.split("-", 2);
                if (value != null && configSplit.length == 2 && configSplit[0] != "") {
                    if (configSplit[0] == config.architecture) {
                        toolChainPathCache.put(config, value)
                        return value
                    }
                }
            }
        }
        toolChainPathCache.put(config, config.toolChainPath)
        return config.toolChainPath
    }

    static final List<BuildConfig> buildConfigs = []

    /**
     * Gets the toolChainPath for the specific build configuration
     */
    public static ArrayList<BuildConfig> getBuildConfigs() {
        return buildConfigs
    }

    /**
     * Gets the extraction platform path for the specific build configuration
     */
    public static String getPlatformPath(BuildConfig config) {
        return config.operatingSystem + '/' + config.architecture
    }

    /**
     * Gets the artifact classifier for a specifc build configuration
     */
    public static String getClassifier(BuildConfig config) {
        return config.operatingSystem + config.architecture
    }

    /**
     * Gets the extraction platform path for a specific binary
     */
    public static String getPlatformPath(NativeBinarySpec binary) {
        return binary.targetPlatform.operatingSystem.name + '/' + binary.targetPlatform.architecture.name
    }

    /**
     * Gets the artifact classifier for a specific binary
     */
    public static String getClassifier(NativeBinarySpec binary) {
        return binary.targetPlatform.operatingSystem.name + binary.targetPlatform.architecture.name
    }

    /**
     * Sets an include flag in the compiler that is platform specific
     */
    public static String setPlatformSpecificIncludeFlag(String loc, Tool cppCompiler) {
        if (OperatingSystem.current().isWindows()) {
            cppCompiler.args "/I$loc"
        } else {
            cppCompiler.args '-I', loc
        }
    }

    @Override
    void apply(Project project) {
        project.ext.BuildConfig = edu.wpi.first.nativeutils.configs.BuildConfig
        project.ext.CrossBuildConfig = edu.wpi.first.nativeutils.configs.CrossBuildConfig

        project.pluginManager.apply(edu.wpi.first.nativeutils.rules.BuildConfigRules)
    }
}
