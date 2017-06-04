package edu.wpi.first.nativeutils

import org.gradle.model.*

class BuildConfigRulesBase extends RuleSource {
    private static final ToolSearchPath toolSearchPath = new ToolSearchPath(OperatingSystem.current())

    @SuppressWarnings("GroovyUnusedDeclaration")
    @Model('buildConfigs')
    void createBuildConfigs(BuildConfigSpec configs) {}

    protected String binTools(String tool, ProjectLayout projectLayout, BuildConfig config) {
        def toolChainPath = NativeUtils.getToolChainPath(config, projectLayout.projectIdentifier)
        def compilerPrefix = config.toolChainPrefix
        if (toolChainPath != null) return "${toolChainPath}/${compilerPrefix}${tool}"
        return "${compilerPrefix}${tool}"
    }

}