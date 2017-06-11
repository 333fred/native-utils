package edu.wpi.first.nativeutils.configs

import org.gradle.model.*
import org.gradle.api.Named
import org.gradle.api.tasks.SourceSet

@Managed
interface JNIConfig extends Named {
    @SuppressWarnings("GroovyUnusedDeclaration")
        void setJniDefinitionClasses(List<String> classes)

        List<String> getJniDefinitionClasses()

        void setJniArmHeaderLocation(File location)

        File getJniArmHeaderLocation()

        @Unmanaged
        void setSourceSets(List<SourceSet> sources)
        @Unmanaged
        List<SourceSet> getSourceSets()
}