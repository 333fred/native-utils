package edu.wpi.first.nativeutils

import org.gradle.api.Plugin
import org.gradle.api.Project

public class ExportsUtils implements Plugin<Project> {
    static File extractedFile = null

    static String getGeneratorFilePath() {
        if (extractedFile != null) {
            return extractedFile.toString()
        }

        InputStream is = ExportsUtils.class.getResourceAsStream("/DefFileGenerator.exe");
        extractedFile = File.createTempFile("DefFileGenerator", ".exe")
        extractedFile.deleteOnExit();

        OutputStream os = new FileOutputStream(extractedFile);

        byte[] buffer = new byte[1024];
        int readBytes;
        try {
            while ((readBytes = is.read(buffer)) != -1) {
            os.write(buffer, 0, readBytes);
            }
        } finally {
            os.close();
            is.close();
        }

        return extractedFile.toString()
    }

    @Override
    void apply(Project project) {
        project.ext.ExportsConfig = edu.wpi.first.nativeutils.ExportsConfig

        project.pluginManager.apply(edu.wpi.first.nativeutils.ExportsConfigRules)
    }
}