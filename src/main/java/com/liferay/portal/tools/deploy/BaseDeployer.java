/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.tools.deploy;

import com.liferay.petra.function.UnsafeConsumer;
import com.liferay.petra.string.StringPool;
import com.liferay.petra.xml.DocUtil;
import com.liferay.petra.xml.XMLUtil;
import com.liferay.portal.deploy.DeployUtil;
import com.liferay.portal.kernel.deploy.Deployer;
import com.liferay.portal.kernel.deploy.auto.AutoDeployException;
import com.liferay.portal.kernel.deploy.auto.AutoDeployer;
import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;
import com.liferay.portal.kernel.plugin.License;
import com.liferay.portal.kernel.plugin.PluginPackage;
import com.liferay.portal.kernel.servlet.*;
import com.liferay.portal.kernel.servlet.filters.invoker.InvokerFilter;
import com.liferay.portal.kernel.util.*;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.DocumentException;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.UnsecureSAXReaderUtil;
import com.liferay.portal.plugin.PluginPackageUtil;
import com.liferay.portal.tools.WebXMLBuilder;
import com.liferay.portal.tools.deploy.extension.DeploymentExtension;
import com.liferay.portal.util.ExtRegistry;
import com.liferay.portal.util.PropsUtil;
import com.liferay.portal.util.PropsValues;
import com.liferay.portal.webserver.DynamicResourceServlet;
import com.liferay.util.ant.CopyTask;
import com.liferay.util.ant.DeleteTask;
import com.liferay.util.ant.ExpandTask;
import com.liferay.util.ant.WarTask;
import org.apache.oro.io.GlobFilenameFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Brian Wing Shun Chan
 * @author Sandeep Soni
 */
public class BaseDeployer implements AutoDeployer, Deployer {

    private static final Logger logger = LoggerFactory.getLogger(BaseDeployer.class);

    private static final String _PORTAL_CLASS_LOADER = "com.liferay.support.tomcat.loader.PortalClassLoader";
    private static final String LOGGING_PROPERTIES = "logging.properties";
    private static final String WEB_INF_CLASSES = "/WEB-INF/classes";
    private static final String LOG_4_J_PROPERTIES = "log4j.properties";
    private static final String CONTEXT_XML = "context.xml";
    private static final String META_INF_CONTEXT_XML = "META-INF/context.xml";
    private static final String WEB_INF = "/WEB-INF";
    private static final String WEB_XML = "web.xml";
    private static final String WEB_INF_WEB_XML = "/WEB-INF/web.xml";
    private static final String MERGE = "/merge/";
    private static final String WAR_EXT = ".war";
    private static final String XML_EXT = ".xml";
    private static final double XML_VERSION_2_3 = 2.3;
    private static final String WEB_INF_LIFERAY_PLUGIN_PACKAGE_PROPERTIES = "/WEB-INF/liferay-plugin-package.properties";
    private static final double WEB_XML_VERSION_2_4 = 2.4;
    private static final String SESSION_FILTERS_WEB_XML = "session-filters-web.xml";
    private static final String SPEED_FILTERS_WEB_XML = "speed-filters-web.xml";
    private static final String LIFERAY_PLUGIN_PACKAGE_XML = "liferay-plugin-package.xml";
    private static final String WEB_INF_CLASSES_SERVICE_PROPERTIES = "/WEB-INF/classes/service.properties";
    private static final String WEB_INF_CLASSES_PORTLET_PROPERTIES = "/WEB-INF/classes/portlet.properties";
    private static final String WEB_INF_LIB = "/WEB-INF/lib/";
    public static final String WEB_INF_LIFERAY_PLUGIN_PACKAGE_XML = "/WEB-INF/liferay-plugin-package.xml";
    private final Set<Path> tempDirPaths = new HashSet<>();
    private final List<DeploymentExtension> _deploymentExtensions = new ArrayList<>();
    private String appServerType;
    private String auiTaglibDTD;
    private String baseDir;
    private String destDir;
    private String filePattern;
    private List<String> jars;
    private String jbossPrefix;
    private String portletExtTaglibDTD;
    private String portletTaglibDTD;
    private String securityTaglibDTD;
    private String themeTaglibDTD;
    private String tomcatLibDir;
    private String uiTaglibDTD;
    private boolean unpackWar;
    private String utilTaglibDTD;
    private List<String> wars;
    private String wildflyPrefix;

    public BaseDeployer() {
        ServiceLoader<DeploymentExtension> serviceLoader = ServiceLoader.load(DeploymentExtension.class,
                BaseDeployer.class.getClassLoader());

        for (DeploymentExtension deploymentExtension : serviceLoader) {
            _deploymentExtensions.add(deploymentExtension);
        }
    }

    @Override
    public void addExtJar(List<String> jars, String resource) throws Exception {
        Set<String> servletContextNames = ExtRegistry.getServletContextNames();

        for (String servletContextName : servletContextNames) {
            String extResource = "ext-" + servletContextName + resource.substring(3);

            String path = DeployUtil.getResourcePath(tempDirPaths, extResource);

            if (path != null) {
                jars.add(path);
            }
        }
    }

    @Override
    public void addRequiredJar(List<String> jars, String resource) throws Exception {

        String path = DeployUtil.getResourcePath(tempDirPaths, resource);

        if (path == null) {
            throw new RuntimeException("Resource " + resource + " does not exist");
        }

        jars.add(path);
    }

    @Override
    public int autoDeploy(AutoDeploymentContext autoDeploymentContext) throws AutoDeployException {

        List<String> wars = new ArrayList<>();

        File file = autoDeploymentContext.getFile();

        wars.add(file.getName());

        this.wars = wars;

        try {
            return deployFile(autoDeploymentContext);
        } catch (Exception e) {
            throw new AutoDeployException(e);
        }
    }

    @Override
    public void checkArguments() {

    }

    @Override
    public AutoDeployer cloneAutoDeployer() throws AutoDeployException {
        try {
            Class<? extends BaseDeployer> clazz = getClass();

            BaseDeployer baseDeployer = clazz.newInstance();

            baseDeployer.setAppServerType(appServerType);
            baseDeployer.setAuiTaglibDTD(auiTaglibDTD);
            baseDeployer.setBaseDir(baseDir);
            baseDeployer.setDestDir(destDir);
            baseDeployer.setFilePattern(filePattern);
            baseDeployer.setJars(jars);
            baseDeployer.setJbossPrefix(jbossPrefix);
            baseDeployer.setPortletExtTaglibDTD(portletExtTaglibDTD);
            baseDeployer.setPortletTaglibDTD(portletTaglibDTD);
            baseDeployer.setSecurityTaglibDTD(securityTaglibDTD);
            baseDeployer.setThemeTaglibDTD(themeTaglibDTD);
            baseDeployer.setTomcatLibDir(tomcatLibDir);
            baseDeployer.setUiTaglibDTD(uiTaglibDTD);
            baseDeployer.setUnpackWar(unpackWar);
            baseDeployer.setUtilTaglibDTD(utilTaglibDTD);
            baseDeployer.setWars(wars);
            baseDeployer.setWildflyPrefix(wildflyPrefix);

            return baseDeployer;
        } catch (Exception e) {
            throw new AutoDeployException(e);
        }
    }

    @Override
    public void close() throws IOException {
        UnsafeConsumer.accept(tempDirPaths, DeployUtil::deletePath, IOException.class);
    }

    @Override
    public void copyDependencyXml(String fileName, String targetDir) throws Exception {
        copyDependencyXml(fileName, targetDir, null);
    }

    @Override
    public void copyDependencyXml(String fileName, String targetDir, Map<String, String> filterMap) throws Exception {
        copyDependencyXml(fileName, targetDir, filterMap, false);
    }

    @Override
    public void copyDependencyXml(String fileName, String targetDir, Map<String, String> filterMap, boolean overwrite)
            throws Exception {
        DeployUtil.copyDependencyXml(fileName, targetDir, fileName, filterMap, overwrite);
    }

    @Override
    public void copyJars(File srcFile, PluginPackage pluginPackage) throws Exception {

        for (String jarFullName : jars) {
            String jarName = jarFullName.substring(jarFullName.lastIndexOf("/") + 1);

            if (!FileUtil.exists(jarFullName)) {
                DeployUtil.getResourcePath(tempDirPaths, jarName);
            }

            FileUtil.copyFile(jarFullName, srcFile + WEB_INF_LIB + jarName, false);
        }
    }

    public void copyPortalDependencies(File srcFile) throws Exception {
        Properties properties = getPluginPackageProperties(srcFile).orElse(new Properties());

        if (properties.isEmpty()) {
            return;
        }

        // jars

        final String jarsDotProperty = properties.getProperty("portal.dependency.jars");
        final String jarsProperty = properties.getProperty("portal-dependency-jars", jarsDotProperty);
        String[] portalJars = StringUtil.split(jarsProperty);

        for (String portalJar : portalJars) {
            portalJar = portalJar.trim();

            portalJar = fixPortalDependencyJar(portalJar);

            try {
                String portalJarPath = PortalUtil.getPortalLibDir() + portalJar;

                FileUtil.copyFile(portalJarPath, srcFile + WEB_INF_LIB + portalJar, true);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

        // tlds

        final String tldsDotProperty = properties.getProperty("portal.dependency.tlds");
        final String tldsProperty = properties.getProperty("portal-dependency-tlds", tldsDotProperty);
        final String[] portalTlds = StringUtil.split(tldsProperty);

        for (String portalTld : portalTlds) {
            portalTld = portalTld.trim();

            try {
                String portalTldPath = DeployUtil.getResourcePath(tempDirPaths, portalTld);

                FileUtil.copyFile(portalTldPath, srcFile + "/WEB-INF/tld/" + portalTld, true);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

        // commons-logging*.jar

        final File pluginLibDir = new File(srcFile + WEB_INF_LIB);

        if (PropsValues.AUTO_DEPLOY_COPY_COMMONS_LOGGING) {
            final String[] commonsLoggingJars = pluginLibDir.list(new GlobFilenameFilter("commons-logging*.jar"));

            if (ArrayUtil.isEmpty(commonsLoggingJars)) {
                final String portalJarPath = PortalUtil.getPortalLibDir() + "commons-logging.jar";

                FileUtil.copyFile(portalJarPath, srcFile + "/WEB-INF/lib/commons-logging.jar", true);
            }
        }

        // log4j*.jar

        if (PropsValues.AUTO_DEPLOY_COPY_LOG4J) {
            final String[] log4jJars = pluginLibDir.list(new GlobFilenameFilter("log4j*.jar"));

            if (ArrayUtil.isEmpty(log4jJars)) {
                String portalJarPath = PortalUtil.getPortalLibDir() + "log4j.jar";

                FileUtil.copyFile(portalJarPath, srcFile + "/WEB-INF/lib/log4j.jar", true);

                portalJarPath = PortalUtil.getPortalLibDir() + "log4j-extras.jar";

                FileUtil.copyFile(portalJarPath, srcFile + "/WEB-INF/lib/log4j-extras.jar", true);
            }
        }
    }

    @Override
    public void copyProperties(File srcFile, PluginPackage pluginPackage) throws Exception {

        if (PropsValues.AUTO_DEPLOY_COPY_COMMONS_LOGGING) {
            copyDependencyXml(LOGGING_PROPERTIES, srcFile + WEB_INF_CLASSES);
        }

        if (PropsValues.AUTO_DEPLOY_COPY_LOG4J) {
            copyDependencyXml(LOG_4_J_PROPERTIES, srcFile + WEB_INF_CLASSES);
        }

        File servicePropertiesFile = new File(srcFile.getAbsolutePath() + WEB_INF_CLASSES_SERVICE_PROPERTIES);

        if (!servicePropertiesFile.exists()) {
            return;
        }

        File portletPropertiesFile = new File(srcFile.getAbsolutePath() + WEB_INF_CLASSES_PORTLET_PROPERTIES);

        if (portletPropertiesFile.exists()) {
            return;
        }

        String pluginPackageName = Optional.ofNullable(pluginPackage)
                .map(PluginPackage::getName).orElse(srcFile.getName());

        FileUtil.write(portletPropertiesFile, "plugin.package.name=" + pluginPackageName);
    }

    @Override
    public void copyTlds(File srcFile, PluginPackage pluginPackage) throws Exception {

        if (Validator.isNotNull(auiTaglibDTD)) {
            FileUtil.copyFile(auiTaglibDTD, srcFile + "/WEB-INF/tld/liferay-aui.tld", true);
        }

        if (Validator.isNotNull(portletTaglibDTD)) {
            FileUtil.copyFile(portletTaglibDTD, srcFile + "/WEB-INF/tld/liferay-portlet.tld", true);
            FileUtil.copyFile(DeployUtil.getResourcePath(tempDirPaths, "liferay-portlet_2_0.tld"),
                    srcFile + "/WEB-INF/tld/liferay-portlet_2_0.tld", true);
        }

        if (Validator.isNotNull(portletExtTaglibDTD)) {
            FileUtil.copyFile(portletExtTaglibDTD, srcFile + "/WEB-INF/tld/liferay-portlet-ext.tld", true);
        }

        if (Validator.isNotNull(securityTaglibDTD)) {
            FileUtil.copyFile(securityTaglibDTD,srcFile + "/WEB-INF/tld/liferay-security.tld", true);
        }

        if (Validator.isNotNull(themeTaglibDTD)) {
            FileUtil.copyFile(themeTaglibDTD, srcFile + "/WEB-INF/tld/liferay-theme.tld", true);
        }

        if (Validator.isNotNull(uiTaglibDTD)) {
            FileUtil.copyFile(uiTaglibDTD, srcFile + "/WEB-INF/tld/liferay-ui.tld", true);
        }

        if (Validator.isNotNull(utilTaglibDTD)) {
            FileUtil.copyFile(utilTaglibDTD, srcFile + "/WEB-INF/tld/liferay-util.tld", true);
        }
    }

    protected void copyTomcatContextXml(File targetDir) throws Exception {
        if (!appServerType.equals(ServerDetector.TOMCAT_ID)) {
            return;
        }

        final File targetFile = new File(targetDir, META_INF_CONTEXT_XML);

        if (targetFile.exists()) {
            return;
        }

        final String contextPath = DeployUtil.getResourcePath(tempDirPaths, CONTEXT_XML);

        String content = FileUtil.read(contextPath);

        if (!PropsValues.AUTO_DEPLOY_UNPACK_WAR) {
            content = StringUtil.replace(content, "antiResourceLocking=\"true\"", StringPool.BLANK);
        }

        FileUtil.write(targetFile, content);
    }

    @Override
    public void copyXmls(File srcFile, String displayName, PluginPackage pluginPackage) throws Exception {

        if (appServerType.equals(ServerDetector.JBOSS_ID) || appServerType.equals(ServerDetector.WILDFLY_ID)) {

            final File file = new File(PropsValues.LIFERAY_WEB_PORTAL_DIR);

            copyDependencyXml("jboss-all.xml", srcFile + WEB_INF,
                    Collections.singletonMap("root_war", file.getName()), true);

            copyDependencyXml("jboss-deployment-structure.xml", srcFile + WEB_INF);
        }

        for (DeploymentExtension deploymentExtension : _deploymentExtensions) {
            if (Objects.equals(appServerType, deploymentExtension.getServerId())) {

                deploymentExtension.copyXmls(this, srcFile);
            }
        }

        copyTomcatContextXml(srcFile);

        copyDependencyXml("_servlet_context_include.jsp", srcFile + "/WEB-INF/jsp");

        copyDependencyXml(WEB_XML, srcFile + WEB_INF);
    }

    private void deployDirectory(File srcFile, File mergeDir, File deployDir, String displayName, boolean overwrite,
                                 PluginPackage pluginPackage) throws Exception {

        rewriteFiles(srcFile);

        mergeDirectory(mergeDir, srcFile);

        processPluginPackageProperties(srcFile, displayName, pluginPackage);

        copyJars(srcFile, pluginPackage);
        copyProperties(srcFile, pluginPackage);
        copyTlds(srcFile, pluginPackage);
        copyXmls(srcFile, displayName, pluginPackage);
        copyPortalDependencies(srcFile);

        final File webXml = new File(srcFile + WEB_INF_WEB_XML);

        updateWebXml(webXml, srcFile, displayName, pluginPackage);

        final File extLibGlobalDir = new File(srcFile.getAbsolutePath() + "/WEB-INF/ext-lib/global");

        if (extLibGlobalDir.exists()) {
            final File globalLibDir = new File(PortalUtil.getGlobalLibDir());

            CopyTask.copyDirectory(extLibGlobalDir, globalLibDir, "*.jar", StringPool.BLANK, overwrite, true);
        }

        final File extLibPortalDir = new File(srcFile.getAbsolutePath() + "/WEB-INF/ext-lib/portal");

        if (extLibPortalDir.exists()) {
            final File portalLibDir = new File(PortalUtil.getPortalLibDir());

            CopyTask.copyDirectory(extLibPortalDir, portalLibDir, "*.jar", StringPool.BLANK, overwrite, true);
        }

        if ((deployDir == null) || baseDir.equals(destDir)) {
            return;
        }

        updateDeployDirectory(srcFile);

        String excludes = StringPool.BLANK;

        if (appServerType.equals(ServerDetector.JBOSS_ID) || appServerType.equals(ServerDetector.WILDFLY_ID)) {
            excludes += "**/WEB-INF/lib/log4j.jar,";
        } else if (appServerType.equals(ServerDetector.TOMCAT_ID)) {
            final String[] libs = FileUtil.listFiles(tomcatLibDir);

            for (String lib : libs) {
                excludes += "**/WEB-INF/lib/" + lib + ",";
            }

            final File contextXml = new File(srcFile + "/META-INF/context.xml");

            if (contextXml.exists()) {
                String content = FileUtil.read(contextXml);

                if (content.contains(_PORTAL_CLASS_LOADER)) {
                    excludes += "**/WEB-INF/lib/util-bridges.jar,";
                    excludes += "**/WEB-INF/lib/util-java.jar,";
                    excludes += "**/WEB-INF/lib/util-taglib.jar,";
                }
            }

            try {

                // LEP-2990

                Class.forName("javax.el.ELContext");

                excludes += "**/WEB-INF/lib/el-api.jar,";
            } catch (ClassNotFoundException cnfe) {
                //logger.error(cnfe.getMessage(), cnfe);
            }
        }

        // LPS-11268

        excludes += getPluginPackageProperties(srcFile).map(props -> {
            String deployExcludes = props.getProperty("deploy-excludes");
            String localExclude = "";

            if (deployExcludes != null) {
                localExclude += deployExcludes.trim();

                if (!localExclude.endsWith(",")) {
                    localExclude += ",";
                }
            }

            deployExcludes = props.getProperty("deploy-excludes-" + appServerType);

            if (deployExcludes != null) {
                localExclude += deployExcludes.trim();

                if (!localExclude.endsWith(",")) {
                    localExclude += ",";
                }
            }

            return localExclude;
        });

        if (!unpackWar) {
            final Path tmdDirPath = Paths.get(SystemProperties.get(SystemProperties.TMP_DIR));
            final Path tempDirPath = Files.createTempDirectory(tmdDirPath, null);

            final File tempDir = tempDirPath.toFile();

            excludes += WEB_INF_WEB_XML;

            final File tempFile = new File(tempDir, displayName + WAR_EXT);

            WarTask.war(srcFile, tempFile, excludes, webXml);

            if (isJEEDeploymentEnabled()) {
                File tempWarDir = new File(tempDir.getParent(), deployDir.getName());

                if (tempWarDir.exists()) {
                    tempWarDir.delete();
                }

                if (!tempDir.renameTo(tempWarDir)) {
                    tempWarDir = tempDir;
                }

                DeploymentHandler deploymentHandler = getDeploymentHandler();

                deploymentHandler.deploy(tempWarDir, displayName);

                deploymentHandler.releaseDeploymentManager();

                DeleteTask.deleteDirectory(tempWarDir);
            } else {
                if (!tempFile.renameTo(deployDir)) {
                    WarTask.war(srcFile, deployDir, excludes, webXml);
                }

                if (tempDir.isDirectory()) {
                    DeleteTask.deleteDirectory(tempDir);
                } else {
                    tempDir.delete();
                }
            }
        } else {

            // The deployer might only copy files that have been modified.
            // However, the deployer always copies and overwrites web.xml after
            // the other files have been copied because application servers
            // usually detect that a WAR has been modified based on the web.xml
            // timestamp.

            excludes += "**/WEB-INF/web.xml";

            CopyTask.copyDirectory(srcFile, deployDir, StringPool.BLANK, excludes, overwrite, true);

            CopyTask.copyDirectory(srcFile, deployDir, "**/WEB-INF/web.xml", StringPool.BLANK, true, false);

            if (appServerType.equals(ServerDetector.TOMCAT_ID)) {

                // See org.apache.catalina.startup.HostConfig to see how Tomcat
                // checks to make sure that web.xml was modified 5 seconds after
                // WEB-INF

                final File deployWebXml = new File(deployDir + WEB_INF_WEB_XML);

                deployWebXml.setLastModified(System.currentTimeMillis() + (Time.SECOND * 6));
            }
        }
    }

    public int deployFile(AutoDeploymentContext autoDeploymentContext) throws Exception {

        final File srcFile = autoDeploymentContext.getFile();

        PluginPackage pluginPackage = Optional.ofNullable(autoDeploymentContext.getPluginPackage())
                .orElse(readPluginPackage(srcFile));

        autoDeploymentContext.setPluginPackage(pluginPackage);

        String autoDeploymentContextAppServerType = autoDeploymentContext.getAppServerType();

        if (Validator.isNotNull(autoDeploymentContextAppServerType)) {
            appServerType = autoDeploymentContextAppServerType;
        }

        String specifiedContext = autoDeploymentContext.getContext();

        String displayName = specifiedContext;

        boolean overwrite = false;

        String destDir = this.destDir;

        if (autoDeploymentContext.getDestDir() != null) {
            destDir = autoDeploymentContext.getDestDir();
        }

        File deployDirFile = new File(destDir + StringPool.SLASH + displayName);

        File mergeDirFile = new File(srcFile.getParent() + MERGE + srcFile.getName());

        deployFile(srcFile, mergeDirFile, deployDirFile, displayName, overwrite, pluginPackage);

        return AutoDeployer.CODE_DEFAULT;
    }

    private void deployFile(File srcFile, File mergeDir, File deployDir, String displayName, boolean overwrite,
                            PluginPackage pluginPackage) throws Exception {

        Path tempDirPath = Files.createTempDirectory(Paths.get(SystemProperties.get(SystemProperties.TMP_DIR)), null);

        File tempDir = tempDirPath.toFile();

        ExpandTask.expand(srcFile, tempDir);

        deployDirectory(tempDir, mergeDir, deployDir, displayName, overwrite, pluginPackage);

        DeleteTask.deleteDirectory(tempDir);
    }

    private String fixPortalDependencyJar(String portalJar) {
        if (portalJar.equals("antlr.jar")) {
            portalJar = "antlr2.jar";
        }

        return portalJar;
    }

    private DeploymentHandler getDeploymentHandler() {
        String prefix = "auto.deploy." + ServerDetector.getServerId() + ".jee.";

        String dmId = PropsUtil.get(prefix + "dm.id");
        String dmUser = PropsUtil.get(prefix + "dm.user");
        String dmPassword = PropsUtil.get(prefix + "dm.passwd");
        String dfClassName = PropsUtil.get(prefix + "df.classname");

        return new DeploymentHandler(dmId, dmUser, dmPassword, dfClassName);
    }

    private String getDisplayName(File srcFile) {
        String displayName = srcFile.getName();

        if (StringUtil.endsWith(displayName, WAR_EXT) || StringUtil.endsWith(displayName, XML_EXT)) {
            displayName = displayName.substring(0, displayName.length() - 4);
        }

        if ((appServerType.equals(ServerDetector.JBOSS_ID) &&
                Validator.isNotNull(jbossPrefix) &&
                displayName.startsWith(jbossPrefix)) ||
                (appServerType.equals(ServerDetector.WILDFLY_ID) &&
                        Validator.isNotNull(wildflyPrefix) &&
                        displayName.startsWith(wildflyPrefix))) {

            displayName = displayName.substring(1);
        }

        return displayName;
    }

    private String getDynamicResourceServletContent() {
        StringBundler sb = new StringBundler();

        sb.append("<servlet>");
        sb.append("<servlet-name>");
        sb.append("Dynamic Resource Servlet");
        sb.append("</servlet-name>");
        sb.append("<servlet-class>");
        sb.append(PortalClassLoaderServlet.class.getName());
        sb.append("</servlet-class>");
        sb.append("<init-param>");
        sb.append("<param-name>");
        sb.append("servlet-class");
        sb.append("</param-name>");
        sb.append("<param-value>");
        sb.append(DynamicResourceServlet.class.getName());
        sb.append("</param-value>");
        sb.append("</init-param>");
        sb.append("<load-on-startup>1</load-on-startup>");
        sb.append("</servlet>");

        for (String allowedPath : PropsValues.DYNAMIC_RESOURCE_SERVLET_ALLOWED_PATHS) {

            sb.append("<servlet-mapping>");
            sb.append("<servlet-name>");
            sb.append("Dynamic Resource Servlet");
            sb.append("</servlet-name>");
            sb.append("<url-pattern>");
            sb.append(allowedPath);

            if (!allowedPath.endsWith(StringPool.SLASH)) {
                sb.append(StringPool.SLASH);
            }

            sb.append(StringPool.STAR);
            sb.append("</url-pattern>");
            sb.append("</servlet-mapping>");
        }

        return sb.toString();
    }

    protected String getExtraContent(double webXmlVersion, File srcFile, String displayName) throws Exception {

        if (displayName.startsWith(StringPool.FORWARD_SLASH)) {
            displayName = displayName.substring(1);
        }

        final StringBundler sb = new StringBundler(70);

        sb.append("<display-name>");
        sb.append(displayName);
        sb.append("</display-name>");

        if (webXmlVersion < WEB_XML_VERSION_2_4) {
            sb.append("<context-param>");
            sb.append("<param-name>liferay-invoker-enabled</param-name>");
            sb.append("<param-value>false</param-value>");
            sb.append("</context-param>");
        }

        if (PropsValues.SESSION_VERIFY_SERIALIZABLE_ATTRIBUTE) {
            sb.append("<listener>");
            sb.append("<listener-class>");
            sb.append(SerializableSessionAttributeListener.class.getName());
            sb.append("</listener-class>");
            sb.append("</listener>");
        }

        sb.append(getDynamicResourceServletContent());

        boolean hasTaglib = false;

        if (Validator.isNotNull(auiTaglibDTD) || Validator.isNotNull(portletTaglibDTD)
                || Validator.isNotNull(portletExtTaglibDTD) || Validator.isNotNull(securityTaglibDTD)
                || Validator.isNotNull(themeTaglibDTD) || Validator.isNotNull(uiTaglibDTD)
                || Validator.isNotNull(utilTaglibDTD)) {

            hasTaglib = true;
        }

        if (hasTaglib && (webXmlVersion > XML_VERSION_2_3)) {
            sb.append("<jsp-config>");
        }

        if (Validator.isNotNull(auiTaglibDTD)) {
            sb.append("<taglib>");
            sb.append("<taglib-uri>http://liferay.com/tld/aui</taglib-uri>");
            sb.append("<taglib-location>");
            sb.append("/WEB-INF/tld/liferay-aui.tld");
            sb.append("</taglib-location>");
            sb.append("</taglib>");
        }

        if (Validator.isNotNull(portletTaglibDTD)) {
            sb.append("<taglib>");
            sb.append("<taglib-uri>http://java.sun.com/portlet_2_0");
            sb.append("</taglib-uri>");
            sb.append("<taglib-location>");
            sb.append("/WEB-INF/tld/liferay-portlet_2_0.tld");
            sb.append("</taglib-location>");
            sb.append("</taglib>");
            sb.append("<taglib>");
            sb.append("<taglib-uri>");
            sb.append("http://xmlns.jcp.org/portlet_3_0");
            sb.append("</taglib-uri>");
            sb.append("<taglib-location>");
            sb.append("/WEB-INF/tld/liferay-portlet.tld");
            sb.append("</taglib-location>");
            sb.append("</taglib>");
        }

        if (Validator.isNotNull(portletExtTaglibDTD)) {
            sb.append("<taglib>");
            sb.append("<taglib-uri>");
            sb.append("http://liferay.com/tld/portlet");
            sb.append("</taglib-uri>");
            sb.append("<taglib-location>");
            sb.append("/WEB-INF/tld/liferay-portlet-ext.tld");
            sb.append("</taglib-location>");
            sb.append("</taglib>");
        }

        if (Validator.isNotNull(securityTaglibDTD)) {
            sb.append("<taglib>");
            sb.append("<taglib-uri>");
            sb.append("http://liferay.com/tld/security");
            sb.append("</taglib-uri>");
            sb.append("<taglib-location>");
            sb.append("/WEB-INF/tld/liferay-security.tld");
            sb.append("</taglib-location>");
            sb.append("</taglib>");
        }

        if (Validator.isNotNull(themeTaglibDTD)) {
            sb.append("<taglib>");
            sb.append("<taglib-uri>http://liferay.com/tld/theme</taglib-uri>");
            sb.append("<taglib-location>");
            sb.append("/WEB-INF/tld/liferay-theme.tld");
            sb.append("</taglib-location>");
            sb.append("</taglib>");
        }

        if (Validator.isNotNull(uiTaglibDTD)) {
            sb.append("<taglib>");
            sb.append("<taglib-uri>http://liferay.com/tld/ui</taglib-uri>");
            sb.append("<taglib-location>");
            sb.append("/WEB-INF/tld/liferay-ui.tld");
            sb.append("</taglib-location>");
            sb.append("</taglib>");
        }

        if (Validator.isNotNull(utilTaglibDTD)) {
            sb.append("<taglib>");
            sb.append("<taglib-uri>http://liferay.com/tld/util</taglib-uri>");
            sb.append("<taglib-location>");
            sb.append("/WEB-INF/tld/liferay-util.tld");
            sb.append("</taglib-location>");
            sb.append("</taglib>");
        }

        if (hasTaglib && (webXmlVersion > XML_VERSION_2_3)) {
            sb.append("</jsp-config>");
        }

        return sb.toString();
    }

    protected String getExtraFiltersContent(double webXmlVersion, File srcFile) throws Exception {

        return getSessionFiltersContent();
    }

    protected String getIgnoreFiltersContent(File srcFile) throws Exception {
        final boolean ignoreFiltersEnabled = getPluginPackageProperties(srcFile)
                .map(props -> GetterUtil.getBoolean(props.getProperty("ignore-filters-enabled"), true))
                .orElse(true);

        if (ignoreFiltersEnabled) {
            final String resourcePath = DeployUtil.getResourcePath(tempDirPaths, "ignore-filters-web.xml");
            return FileUtil.read(resourcePath);
        }

        return StringPool.BLANK;
    }

    private String getInvokerFilterContent() {
        StringBundler sb = new StringBundler(5);

        sb.append(getInvokerFilterContent("ASYNC"));
        sb.append(getInvokerFilterContent("ERROR"));
        sb.append(getInvokerFilterContent("FORWARD"));
        sb.append(getInvokerFilterContent("INCLUDE"));
        sb.append(getInvokerFilterContent("REQUEST"));

        return sb.toString();
    }

    private String getInvokerFilterContent(String dispatcher) {
        StringBundler sb = new StringBundler(24);

        sb.append("<filter>");
        sb.append("<filter-name>Invoker Filter - ");
        sb.append(dispatcher);
        sb.append("</filter-name>");
        sb.append("<filter-class>");
        sb.append(InvokerFilter.class.getName());
        sb.append("</filter-class>");
        sb.append("<async-supported>true</async-supported>");
        sb.append("<init-param>");
        sb.append("<param-name>dispatcher</param-name>");
        sb.append("<param-value>");
        sb.append(dispatcher);
        sb.append("</param-value>");
        sb.append("</init-param>");
        sb.append("</filter>");

        sb.append("<filter-mapping>");
        sb.append("<filter-name>Invoker Filter - ");
        sb.append(dispatcher);
        sb.append("</filter-name>");
        sb.append("<url-pattern>/*</url-pattern>");
        sb.append("<dispatcher>");
        sb.append(dispatcher);
        sb.append("</dispatcher>");
        sb.append("</filter-mapping>");

        return sb.toString();
    }

    private String getPluginPackageLicensesXml(List<License> licenses) {
        if (licenses.isEmpty()) {
            return StringPool.BLANK;
        }

        final StringBundler sb = new StringBundler(5 * licenses.size() + 2);

        for (int i = 0; i < licenses.size(); i++) {
            License license = licenses.get(i);

            if (i == 0) {
                sb.append("\r\n");
            }

            sb.append("\t\t<license osi-approved=\"");
            sb.append(license.isOsiApproved());
            sb.append("\">");
            sb.append(license.getName());
            sb.append("</license>\r\n");

            if ((i + 1) == licenses.size()) {
                sb.append("\t");
            }
        }

        return sb.toString();
    }

    private String getPluginPackageLiferayVersionsXml(List<String> liferayVersions) {

        if (liferayVersions.isEmpty()) {
            return StringPool.BLANK;
        }

        final StringBundler sb = new StringBundler(liferayVersions.size() * 3 + 2);

        for (int i = 0; i < liferayVersions.size(); i++) {
            String liferayVersion = liferayVersions.get(i);

            if (i == 0) {
                sb.append("\r\n");
            }

            sb.append("\t\t<liferay-version>");
            sb.append(liferayVersion);
            sb.append("</liferay-version>\r\n");

            if ((i + 1) == liferayVersions.size()) {
                sb.append("\t");
            }
        }

        return sb.toString();
    }

    private Optional<Properties> getPluginPackageProperties(File srcFile) throws IOException {

        File propertiesFile = new File(srcFile + WEB_INF_LIFERAY_PLUGIN_PACKAGE_PROPERTIES);

        if (!propertiesFile.exists()) {
            return Optional.empty();
        }

        String propertiesString = FileUtil.read(propertiesFile);

        return Optional.of(PropertiesUtil.load(propertiesString));
    }

    private String getPluginPackageRequiredDeploymentContextsXml(List<String> requiredDeploymentContexts) {

        if (requiredDeploymentContexts.isEmpty()) {
            return StringPool.BLANK;
        }

        StringBundler sb = new StringBundler(requiredDeploymentContexts.size() * 3 + 2);

        for (int i = 0; i < requiredDeploymentContexts.size(); i++) {
            String requiredDeploymentContext = requiredDeploymentContexts.get(i);

            if (i == 0) {
                sb.append("\r\n");
            }

            sb.append("\t\t<required-deployment-context>");
            sb.append(requiredDeploymentContext);
            sb.append("</required-deployment-context>\r\n");

            if ((i + 1) == requiredDeploymentContexts.size()) {
                sb.append("\t");
            }
        }

        return sb.toString();
    }

    private String getPluginPackageTagsXml(List<String> tags) {
        if (tags.isEmpty()) {
            return StringPool.BLANK;
        }

        final StringBundler sb = new StringBundler(tags.size() * 3 + 2);

        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);

            if (i == 0) {
                sb.append("\r\n");
            }

            sb.append("\t\t<tag>");
            sb.append(tag);
            sb.append("</tag>\r\n");

            if ((i + 1) == tags.size()) {
                sb.append("\t");
            }
        }

        return sb.toString();
    }

    private Map<String, String> getPluginPackageXmlFilterMap(PluginPackage pluginPackage) {

        final List<String> pluginTypes = pluginPackage.getTypes();

        final String pluginType = pluginTypes.get(0);

        final Map<String, String> filterMap = new HashMap<>();

        filterMap.put("author", wrapCDATA(pluginPackage.getAuthor()));
        filterMap.put("change_log", wrapCDATA(pluginPackage.getChangeLog()));
        filterMap.put("licenses", getPluginPackageLicensesXml(pluginPackage.getLicenses()));
        filterMap.put("liferay_versions", getPluginPackageLiferayVersionsXml(pluginPackage.getLiferayVersions()));
        filterMap.put("long_description", wrapCDATA(pluginPackage.getLongDescription()));
        filterMap.put("module_artifact_id", pluginPackage.getArtifactId());
        filterMap.put("module_group_id", pluginPackage.getGroupId());
        filterMap.put("module_version", pluginPackage.getVersion());
        filterMap.put("page_url", pluginPackage.getPageURL());
        filterMap.put("plugin_name", wrapCDATA(pluginPackage.getName()));
        filterMap.put("plugin_type", pluginType);
        filterMap.put("plugin_type_name", TextFormatter.format(pluginType, TextFormatter.J));
        filterMap.put("recommended_deployment_context", pluginPackage.getRecommendedDeploymentContext());
        filterMap.put("required_deployment_contexts",
                getPluginPackageRequiredDeploymentContextsXml(pluginPackage.getRequiredDeploymentContexts()));
        filterMap.put("short_description", wrapCDATA(pluginPackage.getShortDescription()));
        filterMap.put("tags", getPluginPackageTagsXml(pluginPackage.getTags()));

        return filterMap;
    }

    protected String getServletContextIncludeFiltersContent(double webXmlVersion, File srcFile) throws Exception {

        if (webXmlVersion < WEB_XML_VERSION_2_4) {
            return StringPool.BLANK;
        }

        final Properties properties = getPluginPackageProperties(srcFile).orElse(new Properties());

        if (properties.isEmpty()) {
            return StringPool.BLANK;
        }

        if (!GetterUtil.getBoolean(properties.getProperty("servlet-context-include-filters-enabled"), true)) {
            return StringPool.BLANK;
        }

        return FileUtil.read(DeployUtil.getResourcePath(tempDirPaths, "servlet-context-include-filters-web.xml"));
    }


    private String getSessionFiltersContent() throws Exception {
        final String resourcePath = DeployUtil.getResourcePath(tempDirPaths, SESSION_FILTERS_WEB_XML);

        return FileUtil.read(resourcePath);
    }

    protected String getSpeedFiltersContent(File srcFile) throws Exception {
        boolean speedFiltersEnabled = true;

        final Properties properties = getPluginPackageProperties(srcFile).orElse(new Properties());

        if (!properties.isEmpty()) {
            speedFiltersEnabled = GetterUtil.getBoolean(properties.getProperty("speed-filters-enabled"), true);
        }

        if (speedFiltersEnabled) {
            final String resourcePath = DeployUtil.getResourcePath(tempDirPaths, SPEED_FILTERS_WEB_XML);

            return FileUtil.read(resourcePath);
        }

        return StringPool.BLANK;
    }

    private boolean isJEEDeploymentEnabled() {
        final String serverId = ServerDetector.getServerId();
        final String jeeDeploymentEnabled = PropsUtil.get("auto.deploy." + serverId + ".jee.deployment.enabled");

        return GetterUtil.getBoolean(jeeDeploymentEnabled);
    }

    private void mergeDirectory(File mergeDir, File targetDir) {
        if ((mergeDir == null) || !mergeDir.exists()) {
            return;
        }

        CopyTask.copyDirectory(mergeDir, targetDir, null, null, true, false);
    }

    @Override
    public Map<String, String> processPluginPackageProperties(File srcFile, String displayName,
                                                              PluginPackage pluginPackage) throws Exception {

        if (pluginPackage == null) {
            return null;
        }

        final Properties properties = getPluginPackageProperties(srcFile).orElse(new Properties());

        if (properties.isEmpty()) {
            return null;
        }

        final Map<String, String> filterMap = getPluginPackageXmlFilterMap(pluginPackage);

        copyDependencyXml(LIFERAY_PLUGIN_PACKAGE_XML, srcFile + WEB_INF, filterMap, true);

        return filterMap;
    }

    @Override
    public PluginPackage readPluginPackage(File file) {
        if (!file.exists()) {
            return null;
        }

        InputStream is = null;
        ZipFile zipFile = null;

        try {
            boolean parseProps = false;

            final String pluginPackageXml = StringBundler.concat(
                    file.getParent(), MERGE, file.getName(), WEB_INF_LIFERAY_PLUGIN_PACKAGE_XML);

            final String pluginPackageProperties = StringBundler.concat(
                    file.getParent(), MERGE, file.getName(), WEB_INF_LIFERAY_PLUGIN_PACKAGE_PROPERTIES);

            if (file.isDirectory()) {
                String path = file.getPath();

                File pluginPackageXmlFile = new File(pluginPackageXml);

                if (pluginPackageXmlFile.exists()) {
                    is = new FileInputStream(pluginPackageXmlFile);
                } else {
                    pluginPackageXmlFile = new File(path + WEB_INF_LIFERAY_PLUGIN_PACKAGE_XML);

                    if (pluginPackageXmlFile.exists()) {
                        is = new FileInputStream(pluginPackageXmlFile);
                    }
                }

                File pluginPackagePropertiesFile = new File(pluginPackageProperties);

                if ((is == null) && pluginPackagePropertiesFile.exists()) {
                    is = new FileInputStream(pluginPackagePropertiesFile);

                    parseProps = true;
                } else {
                    pluginPackagePropertiesFile = new File(path + WEB_INF_LIFERAY_PLUGIN_PACKAGE_PROPERTIES);

                    if ((is == null) && pluginPackagePropertiesFile.exists()) {
                        is = new FileInputStream(pluginPackagePropertiesFile);

                        parseProps = true;
                    }
                }
            } else {
                zipFile = new ZipFile(file);

                File pluginPackageXmlFile = new File(pluginPackageXml);

                if (pluginPackageXmlFile.exists()) {
                    is = new FileInputStream(pluginPackageXmlFile);
                } else {
                    ZipEntry zipEntry = zipFile.getEntry("WEB-INF/liferay-plugin-package.xml");

                    if (zipEntry != null) {
                        is = zipFile.getInputStream(zipEntry);
                    }
                }

                File pluginPackagePropertiesFile = new File(pluginPackageProperties);

                if ((is == null) && pluginPackagePropertiesFile.exists()) {
                    is = new FileInputStream(pluginPackagePropertiesFile);

                    parseProps = true;
                } else {
                    ZipEntry zipEntry = zipFile.getEntry("WEB-INF/liferay-plugin-package.properties");

                    if ((is == null) && (zipEntry != null)) {
                        is = zipFile.getInputStream(zipEntry);

                        parseProps = true;
                    }
                }
            }

            if (is == null) {
                return null;
            }

            if (parseProps) {
                String displayName = getDisplayName(file);

                String propertiesString = StringUtil.read(is);

                Properties properties = PropertiesUtil.load(propertiesString);

                return PluginPackageUtil.readPluginPackageProperties(displayName, properties);
            }

            String xml = StringUtil.read(is);

            xml = XMLUtil.fixProlog(xml);

            return PluginPackageUtil.readPluginPackageXml(xml);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ioe) {
                }
            }

            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ioe) {
                }
            }
        }

        return null;
    }

    private void rewriteFiles(File srcDir) {
        String[] fileNames = FileUtil.listFiles(srcDir + "/WEB-INF/");

        for (String fileName : fileNames) {
            String shortFileName = GetterUtil.getString(FileUtil.getShortFileName(fileName));

            // LEP-6415

            if (StringUtil.equalsIgnoreCase(shortFileName, "mule-config.xml")) {
                continue;
            }

            String ext = GetterUtil.getString(FileUtil.getExtension(fileName));

            if (!StringUtil.equalsIgnoreCase(ext, "xml")) {
                continue;
            }

            // Make sure to rewrite any XML files to include external entities
            // into same file. See LEP-3142.

            File file = new File(srcDir + "/WEB-INF/" + fileName);

            try {
                Document doc = UnsecureSAXReaderUtil.read(file);

                String content = doc.formattedString(StringPool.TAB, true);

                FileUtil.write(file, content);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private String secureWebXml(String content, boolean hasCustomServletListener, boolean securityManagerEnabled)
            throws IOException, DocumentException {

        if (!hasCustomServletListener && !securityManagerEnabled) {
            return content;
        }

        Document document = UnsecureSAXReaderUtil.read(content);

        Element rootElement = document.getRootElement();

        List<String> listenerClasses = new ArrayList<>();

        List<Element> listenerElements = rootElement.elements("listener");

        for (Element listenerElement : listenerElements) {
            String listenerClass = GetterUtil.getString(listenerElement.elementText("listener-class"));

            if (listenerClass.equals(PluginContextListener.class.getName()) ||
                    listenerClass.equals(SecurePluginContextListener.class.getName())) {

                continue;
            }

            listenerClasses.add(listenerClass);

            listenerElement.detach();
        }

        Element contextParamElement = rootElement.addElement("context-param");

        DocUtil.add(contextParamElement, "param-name", "portalListenerClasses");
        DocUtil.add(contextParamElement, "param-value", StringUtil.merge(listenerClasses));

        if (!securityManagerEnabled) {
            return document.compactString();
        }

        List<Element> servletElements = rootElement.elements("servlet");

        for (Element servletElement : servletElements) {
            Element servletClassElement = servletElement.element("servlet-class");

            String servletClass = GetterUtil.getString(servletClassElement.getText());

            if (servletClass.equals(PortalClassLoaderServlet.class.getName()) ||
                    servletClass.equals(PortalDelegateServlet.class.getName()) ||
                    servletClass.equals(PortletServlet.class.getName())) {

                continue;
            }

            servletClassElement.setText(SecureServlet.class.getName());

            Element initParamElement = servletElement.addElement("init-param");

            DocUtil.add(initParamElement, "param-name", "servlet-class");
            DocUtil.add(initParamElement, "param-value", servletClass);
        }

        return document.compactString();
    }

    @Override
    public void setAppServerType(String appServerType) {
        this.appServerType = appServerType;
    }

    @Override
    public void setAuiTaglibDTD(String auiTaglibDTD) {
        this.auiTaglibDTD = auiTaglibDTD;
    }

    @Override
    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void setDestDir(String destDir) {
        this.destDir = destDir;
    }

    @Override
    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    @Override
    public void setJars(List<String> jars) {
        this.jars = jars;
    }

    @Override
    public void setJbossPrefix(String jbossPrefix) {
        this.jbossPrefix = jbossPrefix;
    }

    @Override
    public void setPortletExtTaglibDTD(String portletExtTaglibDTD) {
        this.portletExtTaglibDTD = portletExtTaglibDTD;
    }

    @Override
    public void setPortletTaglibDTD(String portletTaglibDTD) {
        this.portletTaglibDTD = portletTaglibDTD;
    }

    @Override
    public void setSecurityTaglibDTD(String securityTaglibDTD) {
        this.securityTaglibDTD = securityTaglibDTD;
    }

    @Override
    public void setThemeTaglibDTD(String themeTaglibDTD) {
        this.themeTaglibDTD = themeTaglibDTD;
    }

    @Override
    public void setTomcatLibDir(String tomcatLibDir) {
        this.tomcatLibDir = tomcatLibDir;
    }

    @Override
    public void setUiTaglibDTD(String uiTaglibDTD) {
        this.uiTaglibDTD = uiTaglibDTD;
    }

    @Override
    public void setUnpackWar(boolean unpackWar) {
        this.unpackWar = unpackWar;
    }

    @Override
    public void setUtilTaglibDTD(String utilTaglibDTD) {
        this.utilTaglibDTD = utilTaglibDTD;
    }

    @Override
    public void setWars(List<String> wars) {
        this.wars = wars;
    }

    @Override
    public void setWildflyPrefix(String wildflyPrefix) {
        this.wildflyPrefix = wildflyPrefix;
    }

    public void updateDeployDirectory(File srcFile) throws Exception {}

    private String updateLiferayWebXml(double webXmlVersion, File srcFile, String webXmlContent) throws Exception {

        final boolean liferayWebXmlEnabled = getPluginPackageProperties(srcFile)
                .map(props -> GetterUtil.getBoolean(props.getProperty("liferay-web-xml-enabled"), true))
                .orElse(true);

        webXmlContent = WebXMLBuilder.organizeWebXML(webXmlContent);

        int x = webXmlContent.indexOf("<filter>");
        int y = webXmlContent.lastIndexOf("</filter-mapping>");

        String webXmlFiltersContent = StringPool.BLANK;

        if ((x == -1) || (y == -1)) {
            x = webXmlContent.lastIndexOf("</display-name>") + 15;

            y = x;
        } else {
            if (liferayWebXmlEnabled && (webXmlVersion > XML_VERSION_2_3)) {
                webXmlFiltersContent = webXmlContent.substring(x, y + 17);

                y = y + 17;
            } else {
                x = y + 17;
                y = y + 17;
            }
        }

        if (webXmlVersion < WEB_XML_VERSION_2_4) {
            webXmlContent = webXmlContent.substring(0, x) + getExtraFiltersContent(webXmlVersion, srcFile) +
                    webXmlContent.substring(y);

            return webXmlContent;
        }

        String filtersContent = webXmlFiltersContent + getExtraFiltersContent(webXmlVersion, srcFile);

        String liferayWebXmlContent = FileUtil.read(
                DeployUtil.getResourcePath(tempDirPaths, WEB_XML));

        int z = liferayWebXmlContent.indexOf("</web-app>");

        liferayWebXmlContent = liferayWebXmlContent.substring(0, z) + filtersContent +
                liferayWebXmlContent.substring(z);

        liferayWebXmlContent = WebXMLBuilder.organizeWebXML(liferayWebXmlContent);

        FileUtil.write(srcFile + "/WEB-INF/liferay-web.xml", liferayWebXmlContent);

        webXmlContent = webXmlContent.substring(0, x) + getInvokerFilterContent() + webXmlContent.substring(y);

        return webXmlContent;
    }

    @Override
    public void updateWebXml(File webXml, File srcFile, String displayName, PluginPackage pluginPackage)
            throws Exception {

        // Check version

        String content = FileUtil.read(webXml);

        content = WebXMLBuilder.organizeWebXML(content);

        int x = content.indexOf("<display-name>");

        if (x != -1) {
            int y = content.indexOf("</display-name>", x);

            y = content.indexOf('>', y) + 1;

            content = content.substring(0, x) + content.substring(y);
        }

        Document document = UnsecureSAXReaderUtil.read(content);

        Element rootElement = document.getRootElement();

        double webXmlVersion = GetterUtil.getDouble(
                rootElement.attributeValue("version"), XML_VERSION_2_3);

        if (webXmlVersion <= XML_VERSION_2_3) {
            throw new AutoDeployException(webXml.getName() + " must be updated to the Servlet 2.4 specification");
        }

        // Plugin context listener

        StringBundler sb = new StringBundler(5);

        sb.append("<listener>");
        sb.append("<listener-class>");

        boolean hasCustomServletListener = false;

        List<Element> listenerElements = rootElement.elements("listener");

        for (Element listenerElement : listenerElements) {
            String listenerClass = GetterUtil.getString(
                    listenerElement.elementText("listener-class"));

            if (listenerClass.equals(PluginContextListener.class.getName()) ||
                    listenerClass.equals(SerializableSessionAttributeListener.class.getName()) ||
                    listenerClass.equals(SecurePluginContextListener.class.getName())) {

                continue;
            }

            hasCustomServletListener = true;

            break;
        }

        boolean securityManagerEnabled = getPluginPackageProperties(srcFile)
                .map(props -> GetterUtil.getBoolean(props.getProperty("security-manager-enabled")))
                .orElse(false);

        if (hasCustomServletListener || securityManagerEnabled) {
            sb.append(SecurePluginContextListener.class.getName());
        } else {
            sb.append(PluginContextListener.class.getName());
        }

        sb.append("</listener-class>");
        sb.append("</listener>");

        String pluginContextListenerContent = sb.toString();

        // Merge content

        String extraContent = getExtraContent(webXmlVersion, srcFile, displayName);

        int pos = content.indexOf("<listener>");

        if (pos == -1) {
            pos = content.indexOf("</web-app>");
        }

        String newContent = StringBundler.concat(
                content.substring(0, pos), pluginContextListenerContent,
                extraContent, content.substring(pos));

        // Update liferay-web.xml

        newContent = updateLiferayWebXml(webXmlVersion, srcFile, newContent);

        // Update web.xml

        newContent = secureWebXml(newContent, hasCustomServletListener, securityManagerEnabled);

        newContent = WebXMLBuilder.organizeWebXML(newContent);

        FileUtil.write(webXml, newContent, true);
    }

    @Override
    public String wrapCDATA(String string) {
        return StringPool.CDATA_OPEN + string + StringPool.CDATA_CLOSE;
    }

}
