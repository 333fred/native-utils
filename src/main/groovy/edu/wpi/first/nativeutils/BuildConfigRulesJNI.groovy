package edu.wpi.first.nativeutils

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.internal.ProjectLayout
import org.gradle.model.*
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.platform.base.BinaryContainer
import org.gradle.api.file.FileTree

@SuppressWarnings("GroovyUnusedDeclaration")
class BuildConfigRulesJNI extends RuleSource {

    @Mutate
    void createJniTasks(ModelMap<Task> tasks, JNIConfig jniConfig, ProjectLayout projectLayout,
                        BinaryContainer binaries, BuildTypeContainer buildTypes, BuildConfigSpec configs) {
        def project = projectLayout.projectIdentifier
        def generatedJNIHeaderLoc = "${project.buildDir}/jniinclude"

        tasks.create('jniHeaders') {
            def outputFolder = project.file(generatedJNIHeaderLoc)
            inputs.files project.sourceSets.main.output
            outputs.file outputFolder
            doLast {
                outputFolder.mkdirs()
                project.exec {
                    executable org.gradle.internal.jvm.Jvm.current().getExecutable('javah')
                    args '-d', outputFolder
                    args '-classpath', project.sourceSets.main.output.classesDir
                    jniConfig.jniDefinitionClasses.each {
                        args it
                    }
                }
            }
        }

        def jniHeaders = tasks.get('jniHeaders')

        def getJniSymbols = {
            def symbolsList = []

            jniHeaders.outputs.files.each {
                FileTree tree = project.fileTree(dir: it)
                tree.each { File file ->
                    file.eachLine { line ->
                        if (line.trim()) {
                            if (line.startsWith("JNIEXPORT ") && line.contains('JNICALL')) {
                                def (p1, p2) = line.split('JNICALL').collect { it.trim() }
                                // p2 is our JNI call
                                symbolsList << p2
                            }
                        }
                    }
                }
            }

            return symbolsList
        }

        configs.findAll { BuildConfigRulesBase.isConfigEnabled(it, projectLayout) }.each { config ->
            binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }.each { binary ->
                if (binary.targetPlatform.architecture.name == config.architecture
                    && binary.targetPlatform.operatingSystem.name == config.operatingSystem
                    && binary.targetPlatform.operatingSystem.name != 'windows'
                    && binary instanceof SharedLibraryBinarySpec) {
                    def input = binary.buildTask.name
                    def linkTaskName = 'link' + input.substring(0, 1).toUpperCase() + input.substring(1);
                    def task = tasks.get(linkTaskName)
                    task.doLast {
                        def library = task.outputFile.absolutePath
                        def nmOutput = "${BuildConfigRulesBase.binTools('nm', projectLayout, config)} ${library}".execute().text

                        def nmSymbols = nmOutput.toString().replace('\r', '')

                        def symbolList = getJniSymbols()

                        symbolList.each {
                            //Add \n so we can check for the exact symbol
                            def found = nmSymbols.contains(it + '\n')
                            if (!found) {
                                throw new GradleException("Found a definition that does not have a matching symbol ${it}")
                            }
                        }
                    }
                }
            }
        }

        configs.findAll { BuildConfigRulesBase.isConfigEnabled(it, projectLayout) }.each { config ->
            binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }.each { binary ->
                if (binary.targetPlatform.architecture.name == config.architecture
                && binary.targetPlatform.operatingSystem.name == config.operatingSystem ) {
                    if (config.crossCompile) {
                        binary.cppCompiler.args '-I', jniConfig.jniArmHeaderLocation.absolutePath
                        binary.cppCompiler.args '-I', jniConfig.jniArmHeaderLocation.absolutePath + '/linux'
                    } else {
                        def jdkLocation = org.gradle.internal.jvm.Jvm.current().javaHome
                        NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include", binary.cppCompiler)
                        if (binary.targetPlatform.operatingSystem.macOsX) {
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/darwin", binary.cppCompiler)
                        } else if (binary.targetPlatform.operatingSystem.linux) {
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/linux", binary.cppCompiler)
                        } else if (binary.targetPlatform.operatingSystem.windows) {
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/win32", binary.cppCompiler)
                        } else if (binary.targetPlatform.operatingSystem.freeBSD) {
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/freebsd", binary.cppCompiler)
                        } else if (file("$jdkLocation/include/darwin").exists()) {
                            // TODO: As of Gradle 2.8, targetPlatform.operatingSystem.macOsX returns false
                            // on El Capitan. We therefore manually test for the darwin folder and include it
                            // if it exists
                            NativeUtils.setPlatformSpecificIncludeFlag("${jdkLocation}/include/darwin", binary.cppCompiler)
                        }
                    }
                    jniHeaders.outputs.files.each { file ->
                        if (config.crossCompile) {
                            binary.cppCompiler.args '-I', file.getPath()
                        } else {
                            NativeUtils.setPlatformSpecificIncludeFlag(file.getPath(), binary.cppCompiler)
                        }
                    }

                    binary.buildTask.dependsOn jniHeaders
                }
            }
         }        
    }
}