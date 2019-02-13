package test;

import com.liferay.portal.osgi.web.wab.generator.internal.WabGenerator;
import com.liferay.util.ant.DeleteTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Test {

    private static final Map<String, String[]> parameters = new HashMap<>();

    static {
        parameters.put("Bundle-SymbolicName", new String[]{"liferay-springmvc-sample"});
        parameters.put("Web-ContextPath", new String[]{"/liferay-springmvc-sample"});
    }

    public Test() throws Exception {

        DeleteTask.deleteDirectory("E:\\Git\\liferay-springmvc-sample\\build\\libs\\deploy");

        final File file = new File("E:\\Git\\liferay-springmvc-sample\\build\\libs\\liferay-springmvc-sample.war");

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
