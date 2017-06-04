package edu.wpi.first.nativeutils

import org.gradle.api.file.FileCollection
import org.gradle.api.Project
import org.gradle.nativeplatform.NativeDependencySet

/**
 *
 */
public class WPILibDependencySet implements NativeDependencySet {
    private String m_rootLocation
    private BuildConfig m_buildConfig
    private Project m_project
    private String m_libraryName
    private boolean m_sharedDep

    public WPILibDependencySet(String rootLocation, BuildConfig config, String libraryName, Project project, boolean sharedDep) {
      m_rootLocation = rootLocation
      m_buildConfig = config
      m_libraryName = libraryName
      m_project = project
      m_sharedDep = sharedDep
    }

    public FileCollection getIncludeRoots() {
        return m_project.files("${m_rootLocation}/headers")
    }

    private FileCollection getFiles() {
      def classifier = NativeUtils.getClassifier(m_buildConfig)
      def platformPath = NativeUtils.getPlatformPath(m_buildConfig)
      def dirPath = 'static'
      if (m_sharedDep) {
          dirPath = 'shared'
      }


      def extension
      if (m_buildConfig.operatingSystem == 'windows') {
        if (m_sharedDep) {
            extension = '.dll'
        } else {
            extension = '.lib'
        }
      } else {
        if (m_sharedDep) {
            extension = '.so'
        } else {
            extension = '.a'
        }
      }
      
      def prefix = m_buildConfig.operatingSystem == 'windows' ? '' : 'lib'
      return m_project.files("${m_rootLocation}/${classifier}/${platformPath}/${dirPath}/${prefix}${m_libraryName}${extension}")
    }

    public FileCollection getLinkFiles() {
        return getFiles()
    }

    public FileCollection getRuntimeFiles() {
        return getFiles()
    }
}
