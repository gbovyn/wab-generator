package com.liferay.portal.osgi.web.wab.generator.internal.helper;

import com.liferay.portal.kernel.util.MapUtil;
import com.liferay.portal.kernel.util.ServerDetector;
import com.liferay.portal.tools.deploy.BaseDeployer;
import com.liferay.portal.tools.deploy.PortletDeployer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeployerHelper {

    private static final String TOMCAT_DIR = "TOMCAT_DIR";

    public static BaseDeployer getBaseDeployer(final File parentFile, final Map<String, String[]> parameters) throws Exception {
        BaseDeployer baseDeployer = new PortletDeployer();

        baseDeployer.setBaseDir(parentFile.getAbsolutePath());
        baseDeployer.setAppServerType(ServerDetector.TOMCAT_ID);
        baseDeployer.setJars(new ArrayList<>());
        baseDeployer.setUnpackWar(true);

        setTlds(baseDeployer, parameters);
        setJars(baseDeployer);
        return baseDeployer;
    }

    private static void setTlds(final BaseDeployer baseDeployer, final Map<String, String[]> parameters) {
        String tldFolderPath = MapUtil.getString(parameters, TOMCAT_DIR) + "/webapps/ROOT/WEB-INF/tld/";

        baseDeployer.setAuiTaglibDTD(tldFolderPath + "liferay-aui.tld");
        baseDeployer.setPortletTaglibDTD(tldFolderPath + "liferay-portlet_2_0.tld");
        baseDeployer.setPortletExtTaglibDTD(tldFolderPath + "liferay-portlet-ext.tld");
        baseDeployer.setSecurityTaglibDTD(tldFolderPath + "liferay-security.tld");
        baseDeployer.setThemeTaglibDTD(tldFolderPath + "liferay-theme.tld");
        baseDeployer.setUiTaglibDTD(tldFolderPath + "liferay-ui.tld");
        baseDeployer.setUtilTaglibDTD(tldFolderPath + "liferay-util.tld");
    }

    private static void setJars(final BaseDeployer baseDeployer) throws Exception {
        List<String> jars = new ArrayList<>();

        baseDeployer.addExtJar(jars, "ext-util-bridges.jar");
        baseDeployer.addExtJar(jars, "ext-util-java.jar");
        baseDeployer.addExtJar(jars, "ext-util-taglib.jar");
        baseDeployer.addRequiredJar(jars, "util-bridges.jar");
        baseDeployer.addRequiredJar(jars, "util-java.jar");
        baseDeployer.addRequiredJar(jars, "util-taglib.jar");
        baseDeployer.setJars(jars);
    }
}
