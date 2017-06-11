package edu.wpi.first.nativeutils.configs

import org.gradle.model.*
import org.gradle.api.Named

@Managed
interface CrossBuildConfig extends BuildConfig {

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setCrossCompile(boolean cc)

    boolean getCrossCompile()


    @SuppressWarnings("GroovyUnusedDeclaration")
    void setToolChainPath(String path)

    String getToolChainPath()

}