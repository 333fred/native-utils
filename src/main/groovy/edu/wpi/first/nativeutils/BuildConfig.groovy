package edu.wpi.first.nativeutils

import org.gradle.model.*

@Managed
interface BuildConfig {
    @SuppressWarnings("GroovyUnusedDeclaration")
    void setArchitecture(String arch)

    String getArchitecture()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setIsArm(boolean isArm)

    boolean getIsArm()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setToolChainPath(String path)

    String getToolChainPath()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setOperatingSystem(String os)

    String getOperatingSystem()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setToolChainPrefix(String prefix)

    String getToolChainPrefix()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setCompilerArgs(List<String> args)

    List<String> getCompilerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setDebugCompilerArgs(List<String> args)

    List<String> getDebugCompilerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setDebugLinkerArgs(List<String> args)

    List<String> getDebugLinkerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setReleaseCompilerArgs(List<String> args)

    List<String> getReleaseCompilerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setReleaseLinkerArgs(List<String> args)

    List<String> getReleaseLinkerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setLinkerArgs(List<String> args)

    List<String> getLinkerArgs()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setCrossCompile(boolean cc)

    boolean getCrossCompile()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setCompilerFamily(String family)

    String getCompilerFamily()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setExclude(List<String> toExclude)

    List<String> getExclude()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setStaticDeps(List<String> staticDeps)

    List<String> getStaticDeps()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setSharedDeps(List<String> sharedDeps)

    List<String> getSharedDeps()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setSkipTests(boolean skip)

    boolean getSkipTests()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setDefFile(String defFile)

    String getDefFile()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setDebugStripBinaries(boolean strip)

    boolean getDebugStripBinaries()

    @SuppressWarnings("GroovyUnusedDeclaration")
    void setReleaseStripBinaries(boolean strip)

    boolean getReleaseStripBinaries()

}