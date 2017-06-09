package edu.wpi.first.nativeutils

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.language.base.internal.ProjectLayout
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.model.*
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.api.file.FileTree
import org.gradle.internal.os.OperatingSystem

@SuppressWarnings("GroovyUnusedDeclaration")
class ExportsConfigRules extends RuleSource {

    @Validate
    void zzzzzzzzzzzzzzzzzzzzzzsetupExports(ModelMap<Task> tasks, ExportsConfigSpec configs, ProjectLayout projectLayout, ComponentSpecContainer components) {
        if (!OperatingSystem.current().isWindows()) {
            return
        }

        if (configs == null) {
            return
        }

        if (components == null) {
            return
        }

        def project = projectLayout.projectIdentifier

        configs.each { config->
            components.each { component->
                if (component.name == config.name) {
                    // Component matches config
                    if (component instanceof NativeLibrarySpec) {
                        component.binaries.each { binary->
                            if (binary.targetPlatform.operatingSystem.name == 'windows'
                                && binary instanceof SharedLibraryBinarySpec) {
                                def objDir
                                binary.tasks.withType(CppCompile) {
                                    objDir = it.objectFileDir
                                }

                                def defFile = project.file("${objDir}/exports.def")
                                binary.linker.args "/DEF:${defFile}"

                                def input = binary.buildTask.name
                                def linkTaskName = 'link' + input.substring(0, 1).toUpperCase() + input.substring(1);
                                def task = tasks.get(linkTaskName)
                                task.doFirst {
                                    def exeName = ExportsUtils.getGeneratorFilePath();
                                    println objDir
                                    def files = project.fileTree(objDir).include("**/*.obj")
                                    project.exec {
                                        executable = exeName
                                        args defFile
                                        files.each {
                                            args it
                                        }

                                    }

                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Mutate
    void doThingWithExports(ModelMap<Task> tasks, ExportsConfigSpec exports) {}
}