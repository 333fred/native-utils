package edu.wpi.first.nativeutils

import org.gradle.model.*
import org.gradle.api.Task
import org.gradle.api.Named

@Managed
interface PublishingConfig extends Named  {
    @Unmanaged
    void setExtraPublishingTasks(List<Task> tasks)
    @Unmanaged
    List<Task> getExtraPublishingTasks()

    void setIncludeComponents(List<String> includes)
    List<String> getIncludeComponents()

    void setExcludeComponents(List<String> excludes)
    List<String> getExcludeComponents()

    void setIsJni(boolean isJni)
    boolean getIsJni()

    void setIsNative(boolean isNative)
    boolean getIsNative()

    void setArtifactId(String id)
    String getArtifactId()

    void setGroupId(String id)
    String getGroupId()

    void setVersion(String version)
    String getVersion()
}