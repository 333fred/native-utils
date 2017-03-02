package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by 333fr on 3/1/2017.
 */
class NativeUtils implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.ext.BuildConfig = edu.wpi.first.nativeutils.BuildConfig

        project.pluginManager.apply(edu.wpi.first.nativeutils.BuildConfigRules)
    }
}
