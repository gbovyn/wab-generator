package test;

import com.liferay.petra.string.StringPool;
import com.liferay.portal.osgi.web.wab.generator.internal.WabGenerator;
import com.liferay.util.ant.DeleteTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;

public class Test {

    public Test() throws Exception {

        DeleteTask.deleteDirectory("E:\\Git\\liferay-springmvc-sample\\build\\libs\\deploy");

        final File file = new File("E:\\Git\\liferay-springmvc-sample\\build\\libs\\liferay-springmvc-sample.war");
        final String fileName = file.getName();
        final String bundleSymbolicName = FilenameUtils.removeExtension(fileName);
        String[] tomcatDir = { "E:/liferay71/bundles/tomcat-9.0.6/" };
        String[] deployDir = { "E:/liferay71/bundles/dowab/" };

        final Map<String, String[]> parameters = new HashMap<>();
        parameters.put("Bundle-SymbolicName", new String[] { bundleSymbolicName });
        parameters.put("Web-ContextPath", new String[] { StringPool.SLASH + bundleSymbolicName });
        parameters.put("TOMCAT_DIR", tomcatDir);
        parameters.put("DEPLOY_FOLDER", deployDir);

        new WabGenerator().generate(Test.class.getClassLoader(), file, parameters);
    }

    public static void main(String[] args) {
        try {
            new Test();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
