package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project

public class PublishUtils implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.ext.PublishingConfig = edu.wpi.first.nativeutils.configs.PublishingConfig

        project.pluginManager.apply(edu.wpi.first.nativeutils.rules.PublishingConfigRules)
    }
}