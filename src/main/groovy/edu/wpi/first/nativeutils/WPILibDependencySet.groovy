package edu.wpi.first.nativeutils

import org.gradle.api.file.FileCollection
import org.gradle.api.Project
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeBinarySpec

/**
 *
 */
public class WPILibDependencySet implements NativeDependencySet {
    private String m_rootLocation
    private NativeBinarySpec m_binarySpec
    private Project m_project
    private String m_libraryName
    private boolean m_sharedDep

    public WPILibDependencySet(String rootLocation, NativeBinarySpec binarySpec, String libraryName, Project project, boolean sharedDep) {
      m_rootLocation = rootLocation
      m_binarySpec = binarySpec
      m_libraryName = libraryName
      m_project = project
      m_sharedDep = sharedDep
    }

    public FileCollection getIncludeRoots() {
        return m_project.files("${m_rootLocation}/headers")
    }

    private FileCollection getFiles() {
      def classifier = NativeUtils.getClassifier(m_binarySpec)
      def platformPath = NativeUtils.getPlatformPath(m_binarySpec)
      def dirPath = 'static'
      if (m_sharedDep) {
          dirPath = 'shared'
      }

      def fileList =  m_project.fileTree("${m_rootLocation}/${classifier}/${platformPath}/${dirPath}/").filter { it.isFile() }
      if (m_binarySpec.targetPlatform.operatingSystem.name == 'windows') {
          fileList.filter { it.endsWith('.lib') }
      }

      return m_project.files(fileList.files)
    }

    public FileCollection getLinkFiles() {
        return getFiles()
    }

    public FileCollection getRuntimeFiles() {
        return getFiles()
    }
}
