package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project

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
