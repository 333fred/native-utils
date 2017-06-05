package edu.wpi.first.nativeutils

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.Copy
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.base.internal.ProjectLayout
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.model.*
import org.gradle.nativeplatform.BuildTypeContainer
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec
import org.gradle.nativeplatform.Tool
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.VisualCpp
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.PlatformContainer

@SuppressWarnings("GroovyUnusedDeclaration")
class DependencyConfigRules extends RuleSource {
    @Validate
    void setupDependencies(DependencyConfigSpec configs, BinaryContainer binaries,
                        ProjectLayout projectLayout, BuildConfigSpec buildConfigs) {
        def rootProject = projectLayout.projectIdentifier.rootProject
        def currentProject = projectLayout.projectIdentifier

        currentProject.configurations.create('nativeDeps')

        configs.each { config->
            currentProject.dependencies {
                nativeDeps group: config.groupId, name: config.artifactId, version: config.version, classifier: config.headerClassifier, ext: config.ext
            }
            buildConfigs.findAll { BuildConfigRulesBase.isConfigEnabled(it, projectLayout) }.each { buildConfig ->
                currentProject.dependencies {
                    nativeDeps group: config.groupId, name: config.artifactId, version: config.version, classifier: NativeUtils.getClassifier(buildConfig), ext: config.ext
                }
            }
        }

        def depLocation = "${rootProject.buildDir}/dependencies"

        def filesList = currentProject.configurations.nativeDeps.files

        currentProject.configurations.nativeDeps.dependencies.each { dependency ->
            def classifier = dependency.artifacts[0].classifier
            def extension = dependency.artifacts[0].extension
            def taskName = "download${dependency.group}${dependency.name}${classifier}"
            def task = rootProject.tasks.findByPath(taskName)
            if (task == null) {
                task = rootProject.tasks.create(taskName, Copy) {
                    def file
                    filesList.each {
                        if (it.toString().endsWith("${classifier}.${extension}")) {
                            file = it
                        }
                    }
                    from rootProject.zipTree(file)
                    into "$depLocation/${dependency.name.toLowerCase()}/${classifier}"
                }
                binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }.each { binary ->
                    if (NativeUtils.getClassifier(binary) == classifier || classifier == 'headers') {
                        binary.buildTask.dependsOn task
                    }
                }
            }
        }
        configs.each { config ->
            def nativeBinaries = binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }
            nativeBinaries.each { bin ->
                def component = bin.component
                if (config.sharedConfigs.containsKey(component.name)) {
                    def binariesToApplyTo = nativeBinaries.findAll {
                        config.sharedConfigs.get(component.name).contains("${it.targetPlatform.operatingSystem.name}:${it.targetPlatform.architecture.name}".toString())
                    }
                    binariesToApplyTo.each { binary->
                        binary.lib(new WPILibDependencySet("$depLocation/${config.artifactId.toLowerCase()}", binary, config.artifactId, currentProject, true))
                    }
                }

                if (config.staticConfigs.containsKey(component.name)) {
                    def binariesToApplyTo = nativeBinaries.findAll {
                        config.staticConfigs.get(component.name).contains("${it.targetPlatform.operatingSystem.name}:${it.targetPlatform.architecture.name}".toString())
                    }
                    binariesToApplyTo.each { binary->
                        binary.lib(new WPILibDependencySet("$depLocation/${config.artifactId.toLowerCase()}", binary, config.artifactId, currentProject, false))
                    }
                }
            }
        }
    }

    @Mutate
    void doThingWithDeps(ModelMap<Task> tasks, DependencyConfigSpec configs) {

    }
}