package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project

public class JNIUtils implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (!project.ext.hasProperty('jniDefinitionClasses')) {
            return
        }

        def generatedJNIHeaderLoc = "${project.buildDir}/jniinclude"

        /**
        * Generates the JNI headers
        */
        def jniHeaders = project.tasks.create('jniHeaders') {
            def outputFolder = project.file(generatedJNIHeaderLoc)
            inputs.files project.sourceSets.main.output
            outputs.file outputFolder
            doLast {
                outputFolder.mkdirs()
                project.exec {
                    executable org.gradle.internal.jvm.Jvm.current().getExecutable('javah')
                    args '-d', outputFolder
                    args '-classpath', project.sourceSets.main.output.classesDir
                    project.ext.jniDefinitionClasses.each {
                    args it
                    }
                }
            }
        }

        
    }
}