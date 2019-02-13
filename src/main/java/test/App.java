package test;

import com.liferay.petra.string.StringPool;
import com.liferay.portal.osgi.web.wab.generator.internal.WabGenerator;
import com.liferay.util.ant.DeleteTask;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FilenameUtils;

public class App {

    public App(String filePath) throws Exception {

        DeleteTask.deleteDirectory("E:\\Git\\liferay-springmvc-sample\\build\\libs\\deploy");

        String dowabConfigPath = "dowab.properties";
        System.out.println("Reading properties from: " + new File(dowabConfigPath).getAbsolutePath());
        Properties properties = new Properties();
        properties.load(new FileInputStream(dowabConfigPath));

        final File file = new File(filePath);
        final String fileName = file.getName();
        final String bundleSymbolicName = FilenameUtils.removeExtension(fileName);
        String[] tomcatDir = { properties.getProperty("tomcatFolder") };
        String[] deployDir = { properties.getProperty("deployFolder") };

        System.out.println("Tomcat: " + tomcatDir[0]);
        System.out.println("Deploy: " + deployDir[0]);

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
