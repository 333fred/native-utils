package edu.wpi.first.nativeutils

import org.gradle.model.*
import org.gradle.api.Task

@Managed
interface PublishingConfig {
    @SuppressWarnings("GroovyUnusedDeclaration")
      void setCppPublishingExtension(String extension)

      String getCppPublishingExtension()

      void setCppPublishingExtraArtifacts(List<Task> tasks)

      List<Task> getCppPublishingExtraArtifacts()

      @SuppressWarnings("GroovyUnusedDeclaration")
      void setJniPublishingExtension(String extension)

      String getJniPublishingExtension()
      
      void setJniPublishingExtraArtifacts(List<Task> tasks)

      List<Task> getJniPublishingExtraArtifacts()
}