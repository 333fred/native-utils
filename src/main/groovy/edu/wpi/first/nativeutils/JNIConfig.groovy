package edu.wpi.first.nativeutils

import org.gradle.model.*

@Managed
interface JNIConfig {
    @SuppressWarnings("GroovyUnusedDeclaration")
        void setJniDefinitionClasses(List<String> classes)

        List<String> getJniDefinitionClasses()

        void setJniArmHeaderLocation(File location)

        File getJniArmHeaderLocation()

        void setForceStaticLinks(List<String> staticLinks)

        List<String> getForceStaticLinks()
}