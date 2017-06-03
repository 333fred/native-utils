package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.nativeplatform.NativeBinarySpec

/**
 * Created by 333fr on 3/1/2017.
 */
public class NativeUtils implements Plugin<Project> {
    private static final HashMap<BuildConfig, String> toolChainPathCache = new HashMap<>()

    /**
     * Gets the toolChainPath for the specific build configuration
     */
    public static String getToolChainPath(BuildConfig config, Project project) { 
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

    static final ArrayList<BuildConfig> buildConfigs = new ArrayList<>();

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
        return binary.targetPlatform.operatingSystem + '/' + binary.targetPlatform.architecture
    }

    /**
     * Gets the artifact classifier for a specific binary
     */
    public static String getClassifier(NativeBinarySpec binary) {
        return binary.targetPlatform.operatingSystem + binary.targetPlatform.architecture
    }
    
    @Override
    void apply(Project project) {
        project.ext.BuildConfig = edu.wpi.first.nativeutils.BuildConfig
        
        project.pluginManager.apply(edu.wpi.first.nativeutils.BuildConfigRules)
    }
}
