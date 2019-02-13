package test;

import com.liferay.petra.string.StringPool;
import com.liferay.portal.osgi.web.wab.generator.internal.WabGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FilenameUtils;

public class App {

    public App(String filePath) throws Exception {

        String[] tomcatDir = { getTomcatFolder() };
        String[] deployDir = { getDeployFolder() };

        System.out.println("Tomcat: " + tomcatDir[0]);
        System.out.println("Deploy: " + deployDir[0]);

        final File file = new File(filePath);
        final String fileName = file.getName();
        final String bundleSymbolicName = FilenameUtils.removeExtension(fileName);

        final Map<String, String[]> parameters = new HashMap<>();
        parameters.put("Bundle-SymbolicName", new String[] { bundleSymbolicName });
        parameters.put("Web-ContextPath", new String[] { StringPool.SLASH + bundleSymbolicName });
        parameters.put("TOMCAT_DIR", tomcatDir);
        parameters.put("DEPLOY_FOLDER", deployDir);

        new WabGenerator().generate(null, file, parameters);
    }

    public static void main(String[] args) {
        try {
            String warPath = args[0];
            System.out.println("WAR path: " + warPath);
            new App(warPath);
            System.out.println("Done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getTomcatFolder() throws IOException {
        return getProperty("tomcatFolder");
    }

    private String getDeployFolder() throws IOException {
        return getProperty("deployFolder");
    }

    private String getProperty(final String tomcatFolder) throws IOException {
        String deployFolder = getPropertyFromDowabProperties(tomcatFolder);

        if (deployFolder != null) {
            return deployFolder;
        }

        deployFolder = getPropertyFromGradleProperties(tomcatFolder);

        return deployFolder;
    }

    private String getPropertyFromDowabProperties(String property) throws IOException {
        String dowabConfigPath = "dowab.properties";
        Properties properties = getProperties(dowabConfigPath);

        return properties.getProperty(property);
    }

    private String getPropertyFromGradleProperties(String property) throws IOException {
        String gradlePropertiesPath = System.getenv("USERPROFILE") + "/.gradle/gradle.properties";
        Properties properties = getProperties(gradlePropertiesPath);

        return properties.getProperty(property);
    }

    private Properties getProperties(final String path) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(path));

        return properties;
    }
}
