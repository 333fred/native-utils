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
import org.gradle.api.publish.maven.MavenPublication
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
class PublishingConfigRules extends RuleSource {

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void createNativeZipTasks(ModelMap<Task> tasks, BinaryContainer binaries, BuildTypeContainer buildTypes, 
                        ProjectLayout projectLayout, BuildConfigSpec configs, PublishingConfigSpec publishConfigs,
                        ComponentSpecContainer components) {
        if (publishConfigs == null) {
            return
        }
        def project = projectLayout.projectIdentifier

        def c = publishConfigs.findAll { it.isNative }
        if (c.size() == 0) {
            return
        }
        
        c.each { publishConfig->
            def componentsToBuild = components.findAll { true }
            if (publishConfig.includeComponents == null || publishConfig.includeComponents.size() == 0) {
                // Include all configs except the excludes
                if (publishConfig.excludeComponents != null && publishConfig.excludeComponents.size() > 0) {
                    publishConfig.excludeComponents.each { excludeConfig ->
                        componentsToBuild.removeIf { it.name == excludeConfig }
                    }
                }
            } else {
                componentsToBuild.removeAll { publishConfig.includeComponents.contains(it) }
            }
            componentsToBuild.each { component ->
                buildTypes.each { buildType ->
                    configs.findAll { BuildConfigRulesBase.isConfigEnabled(it, projectLayout) }.each { config ->
                        def base = 'zip' + publishConfig.name + component.name + buildType.name
                        def taskName = base + config.operatingSystem + config.architecture
                        tasks.create(taskName, Zip) { task ->
                            description = 'Creates platform zip of the libraries'
                            destinationDir =  projectLayout.buildDir
                            classifier = config.operatingSystem + config.architecture
                            baseName = base
                            duplicatesStrategy = 'exclude'

                            binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }.each { binary ->
                                if (binary.targetPlatform.architecture.name == config.architecture
                                    && binary.targetPlatform.operatingSystem.name == config.operatingSystem 
                                    && binary.buildType.name == buildType.name
                                    && binary.component.name == component.name) {
                                    if (binary instanceof SharedLibraryBinarySpec) {
                                        dependsOn binary.buildTask
                                        from(new File(binary.sharedLibraryFile.absolutePath + ".debug")) {
                                            into NativeUtils.getPlatformPath(config) + '/shared'
                                        }
                                        from (binary.sharedLibraryFile) {
                                            into NativeUtils.getPlatformPath(config) + '/shared'
                                        }
                                    } else if (binary instanceof StaticLibraryBinarySpec) {
                                        dependsOn binary.buildTask
                                        from (binary.staticLibraryFile) {
                                            into NativeUtils.getPlatformPath(config) + '/static'
                                        }
                                    }
                                }
                            }
                        }

                        def buildTask = tasks.get('build')
                        def zipTask = tasks.get(taskName)
                        buildTask.dependsOn zipTask

                        project.artifacts {
                            zipTask
                        }

                        def publishing = project.publishing
                        def pubs = publishing.publications.findAll { it.name == publishConfig.name }
                        if (pubs.size() == 0) {
                            publishing.publications.create(publishConfig.name, MavenPublication) {
                                it.artifactId = publishConfig.artifactId
                                it.groupId = publishConfig.groupId
                                it.version = publishConfig.version
                            }
                            pubs = publishing.publications.findAll { it.name == publishConfig.name }
                        }
                        def cppPublication = pubs[0]
                        cppPublication.artifact zipTask
                    }
                }
                
                def cppPublication = project.publishing.publications.findAll { it.name == publishConfig.name }[0]
                publishConfig.extraPublishingTasks.each {
                    cppPublication.artifact it
                }
            }
        }
    }

    @SuppressWarnings(["GroovyUnusedDeclaration", "GrMethodMayBeStatic"])
    @Mutate
    void createJniZipTasks(ModelMap<Task> tasks, BinaryContainer binaries, BuildTypeContainer buildTypes, 
                        ProjectLayout projectLayout, BuildConfigSpec configs, JNIConfig jniConfig, PublishingConfigSpec publishConfigs,
                        ComponentSpecContainer components) {

        if (jniConfig == null) {
            return
        }
        if (publishConfigs == null) {
            return
        }
        def project = projectLayout.projectIdentifier

        def c = publishConfigs.findAll { it.isJni }
        if (c.size() == 0) {
            return
        }
        
        c.each { publishConfig->
            def componentsToBuild = components.findAll { true }
            if (publishConfig.includeComponents == null || publishConfig.includeComponents.size() == 0) {
                // Include all configs except the excludes
                if (publishConfig.excludeComponents != null && publishConfig.excludeComponents.size() > 0) {
                    publishConfig.excludeComponents.each { excludeConfig ->
                        componentsToBuild.removeIf { it.name == excludeConfig }
                    }
                }
            } else {
                componentsToBuild.removeAll { publishConfig.includeComponents.contains(it) }
            }
            componentsToBuild.each { component ->
                buildTypes.each { buildType ->
                    configs.findAll { BuildConfigRulesBase.isConfigEnabled(it, projectLayout) }.each { config ->
                        def base = 'jni' + publishConfig.name + component.name + buildType.name
                        def taskName = base + config.operatingSystem + config.architecture
                        tasks.create(taskName, Jar) { task ->
                            description = 'Creates platform jni jar of the libraries'
                            destinationDir =  projectLayout.buildDir
                            classifier = config.operatingSystem + config.architecture
                            baseName = base
                            duplicatesStrategy = 'exclude'

                            binaries.findAll { BuildConfigRulesBase.isNativeProject(it) }.each { binary ->
                                if (binary.targetPlatform.architecture.name == config.architecture
                                && binary.targetPlatform.operatingSystem.name == config.operatingSystem 
                                && binary.buildType.name == buildType.name) {
                                if (binary instanceof SharedLibraryBinarySpec) {
                                    dependsOn binary.buildTask
                                    from (binary.sharedLibraryFile) {
                                        into NativeUtils.getPlatformPath(config)
                                    }
                                }
                            }
                        }
                    }

                        def buildTask = tasks.get('build')
                        def zipTask = tasks.get(taskName)
                        buildTask.dependsOn zipTask

                        project.artifacts {
                            zipTask
                        }

                        def publishing = project.publishing
                        def pubs = publishing.publications.findAll { it.name == publishConfig.name }
                        if (pubs.size() == 0) {
                            publishing.publications.create(publishConfig.name, MavenPublication) {
                                it.artifactId = publishConfig.artifactId
                                it.groupId = publishConfig.groupId
                                it.version = publishConfig.version
                            }
                            pubs = publishing.publications.findAll { it.name == publishConfig.name }
                        }
                        def cppPublication = pubs[0]
                        cppPublication.artifact zipTask
                    }
                }
                
                def cppPublication = project.publishing.publications.findAll { it.name == publishConfig.name }[0]
                publishConfig.extraPublishingTasks.each {
                    cppPublication.artifact it
                }
            }
        }
    }
}