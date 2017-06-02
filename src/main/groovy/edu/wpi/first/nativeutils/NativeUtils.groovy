package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.nativeplatform.NativeBinarySpec

/**
 * Created by 333fr on 3/1/2017.
 */
public class NativeUtils implements Plugin<Project> {
    private static final HashMap<String, String> toolChainPathMaps = new HashMap<String, String>();
    public static String getToolChainPath(BuildConfig config) {
        for( item in toolChainPathMaps) {
            if (item.key == config.architecture) {
                return item.value;
            }
        }
        return config.toolChainPath
    }

    static final ArrayList<BuildConfig> buildConfigs = new ArrayList<>();

    public static ArrayList<BuildConfig> getBuildConfigs() {
        return buildConfigs
    }

    public static String getPlatformPath(BuildConfig config) {
        return config.operatingSystem + '/' + config.architecture
    }

    public static String getClassifier(BuildConfig config) {
        return config.operatingSystem + config.architecture
    }

    public static String getPlatformPath(NativeBinarySpec binary) {
        return binary.targetPlatform.operatingSystem + '/' + binary.targetPlatform.architecture
    }

    public static String getClassifier(NativeBinarySpec binary) {
        return binary.targetPlatform.operatingSystem + binary.targetPlatform.architecture
    }
    
    @Override
    void apply(Project project) {
        project.ext.BuildConfig = edu.wpi.first.nativeutils.BuildConfig

        project.ext.properties.each { key, value ->
            if (key.contains('-toolChainPath')) {
                String[] configSplit = key.split("-", 2);
                if (value != null && configSplit.length == 2 && configSplit[0] != "") {
                    toolChainPathMaps.put(configSplit[0], value);
                }
            }
        }
        
        project.pluginManager.apply(edu.wpi.first.nativeutils.BuildConfigRules)
    }
}
