package edu.wpi.first.nativeutils.rules

import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.language.base.internal.ProjectLayout
import org.gradle.model.*
import org.gradle.platform.base.BinaryContainer
import edu.wpi.first.nativeutils.dependencysets.*
import edu.wpi.first.nativeutils.NativeUtils

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
                        if (it.toString().endsWith("${classifier}.${extension}") && it.toString().contains("${dependency.name}-".toString())) {
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
                if (config.sharedConfigs != null && config.sharedConfigs.containsKey(component.name)) {
                    def binariesToApplyTo = nativeBinaries.findAll {
                        config.sharedConfigs.get(component.name).contains("${it.targetPlatform.operatingSystem.name}:${it.targetPlatform.architecture.name}".toString())
                    }
                    binariesToApplyTo.each { binary->
                        binary.lib(new SharedDependencySet("$depLocation/${config.artifactId.toLowerCase()}", binary, config.artifactId, currentProject))
                    }
                }

                if (config.staticConfigs != null && config.staticConfigs.containsKey(component.name)) {
                    def binariesToApplyTo = nativeBinaries.findAll {
                        config.staticConfigs.get(component.name).contains("${it.targetPlatform.operatingSystem.name}:${it.targetPlatform.architecture.name}".toString())
                    }
                    binariesToApplyTo.each { binary->
                        binary.lib(new StaticDependencySet("$depLocation/${config.artifactId.toLowerCase()}", binary, config.artifactId, currentProject))
                    }
                }
            }
        }
    }

    @Mutate
    void doThingWithDeps(ModelMap<Task> tasks, DependencyConfigSpec configs) {

    }
}