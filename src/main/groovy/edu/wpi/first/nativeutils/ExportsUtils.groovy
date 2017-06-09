package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project

public class ExportsUtils implements Plugin<Project> {
    static String getGeneratorFilePath() {
        return "C:\\Users\\thadh\\Documents\\GitHub\\ThadHouse\\DefFileGenerator\\Build\\Release\\DefFileGenerator.exe"
    }

    @Override
    void apply(Project project) {
        project.ext.ExportsConfig = edu.wpi.first.nativeutils.ExportsConfig

        project.pluginManager.apply(edu.wpi.first.nativeutils.ExportsConfigRules)
    }
}