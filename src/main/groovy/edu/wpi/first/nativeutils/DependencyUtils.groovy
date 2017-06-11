package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project

public class DependencyUtils implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.ext.DependencyConfig = edu.wpi.first.nativeutils.configs.DependencyConfig

        project.pluginManager.apply(edu.wpi.first.nativeutils.rules.DependencyConfigRules)
    }
}