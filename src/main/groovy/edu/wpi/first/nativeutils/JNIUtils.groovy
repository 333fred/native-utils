package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project

public class JNIUtils implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.ext.JNIConfig = edu.wpi.first.nativeutils.configs.JNIConfig

        project.pluginManager.apply(edu.wpi.first.nativeutils.rules.JNIConfigRules)
    }
}