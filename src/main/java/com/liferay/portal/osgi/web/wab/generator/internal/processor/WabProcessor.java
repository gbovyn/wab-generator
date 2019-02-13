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

package com.liferay.portal.osgi.web.wab.generator.internal.processor;

import aQute.bnd.component.DSAnnotations;
import aQute.bnd.header.Attrs;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.*;
import aQute.bnd.service.verifier.VerifierPlugin;
import aQute.bnd.version.Version;
import aQute.lib.filter.Filter;
import com.liferay.ant.bnd.jsp.JspAnalyzerPlugin;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.plugin.PluginPackage;
import com.liferay.portal.kernel.servlet.PortalClassLoaderFilter;
import com.liferay.portal.kernel.servlet.PortalClassLoaderServlet;
import com.liferay.portal.kernel.util.*;
import com.liferay.portal.kernel.xml.*;
import com.liferay.portal.tools.ToolDependencies;
import com.liferay.portal.tools.deploy.BaseDeployer;
import com.liferay.portal.util.PropsValues;
import com.liferay.whip.util.ReflectionUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Raymond Augé
 * @author Miguel Pastor
 */
public class WabProcessor {

    private static final Version _CDI_ARCHIVE_VERSION = new Version(1, 1, 0);
    private static final String _CDI_REQUIREMENTS = StringBundler.concat(
            "osgi.cdi.extension;filter:='(osgi.cdi.extension=aries.cdi.http)',",
            "osgi.cdi.extension;filter:='(osgi.cdi.extension=", "com.liferay.bean.portlet.cdi.extension)'");
    private static final String[] _KNOWN_PROPERTY_KEYS = {"jdbc.driverClassName"};
    private static final String _XPATHS_HBM = StringUtil.merge(
            new String[]{"//class/@name", "//id/@access", "//import/@class", "//property/@type"}, "|");
    private static final String _XPATHS_HOOK = StringUtil.merge(
            new String[]{"//indexer-post-processor-impl", "//service-impl", "//servlet-filter-impl", "//struts-action-impl"}, "|");
    private static final String _XPATHS_JAVAEE = StringUtil.merge(
            new String[]{"//j2ee:filter-class", "//j2ee:listener-class", "//j2ee:servlet-class", "//javaee:filter-class",
                    "//javaee:listener-class", "//javaee:servlet-class"},
            "|");
    private static final String _XPATHS_LIFERAY = StringUtil.merge(
            new String[]{
                    "//asset-renderer-factory", "//atom-collection-adapter",
                    "//configuration-action-class", "//control-panel-entry-class",
                    "//custom-attributes-display", "//friendly-url-mapper-class",
                    "//indexer-class", "//open-search-class", "//permission-propagator",
                    "//poller-processor-class", "//pop-message-listener-class",
                    "//portlet-data-handler-class", "//portlet-layout-listener-class",
                    "//portlet-url-class", "//social-activity-interpreter-class",
                    "//social-request-interpreter-class", "//url-encoder-class",
                    "//webdav-storage-class", "//workflow-handler",
                    "//xml-rpc-method-class"
            },
            "|");
    private static final String _XPATHS_PORTLET = StringUtil.merge(
            new String[]{"//portlet2:filter-class", "//portlet2:listener-class", "//portlet2:portlet-class", "//portlet2:resource-bundle"}, "|");

    private static final String _XPATHS_SPRING = StringUtil.merge(
            new String[]{
                    "//beans:bean/@class", "//beans:*/@value-type",
                    "//aop:*/@implement-interface", "//aop:*/@default-impl",
                    "//context:load-time-weaver/@weaver-class",
                    "//jee:jndi-lookup/@expected-type",
                    "//jee:jndi-lookup/@proxy-interface", "//jee:remote-slsb/@ejbType",
                    "//jee:*/@business-interface", "//lang:*/@script-interfaces",
                    "//osgi:*/@interface", "//gemini-blueprint:*/@interface",
                    "//blueprint:*/@interface", "//blueprint:*/@class",
                    "//util:list/@list-class", "//util:set/@set-class",
                    "//util:map/@map-class", "//webflow-config:*/@class"
            },
            "|");

    private static final Log _log = LogFactoryUtil.getLog(WabProcessor.class);
    private static final Attrs _optionalAttrs = new Attrs() {
        {
            put("resolution:", "optional");
        }
    };
    private static final Pattern _tldPackagesPattern = Pattern.compile(
            "<[^>]+?-class>\\p{Space}*?(.*?)\\p{Space}*?</[^>]+?-class>");
    private static final Pattern _versionMavenPattern = Pattern.compile(
            "(\\d{1,9})(\\.(\\d{1,9})(\\.(\\d{1,9})(-([-_\\da-zA-Z]+))?)?)?");
    private static final Map<String, String> _xsds =
            new ConcurrentHashMap<String, String>() {
                {
                    put("aop", "http://www.springframework.org/schema/aop");
                    put("beans", "http://www.springframework.org/schema/beans");
                    put("blueprint", "http://www.osgi.org/xmlns/blueprint/v1.0.0");
                    put("cdi-beans", "http://xmlns.jcp.org/xml/ns/javaee");
                    put("context", "http://www.springframework.org/schema/context");
                    put("gemini-blueprint", "http://www.eclipse.org/gemini/blueprint/schema/blueprint");
                    put("j2ee", "http://java.sun.com/xml/ns/j2ee");
                    put("javaee", "http://java.sun.com/xml/ns/javaee");
                    put("jee", "http://www.springframework.org/schema/jee");
                    put("jms", "http://www.springframework.org/schema/jms");
                    put("lang", "http://www.springframework.org/schema/lang");
                    put("osgi", "http://www.springframework.org/schema/osgi");
                    put("osgi-compendium", "http://www.springframework.org/schema/osgi-compendium");
                    put("portlet2", "http://java.sun.com/xml/ns/portlet/portlet-app_2_0.xsd");
                    put("tool", "http://www.springframework.org/schema/tool");
                    put("tx", "http://www.springframework.org/schema/tx");
                    put("util", "http://www.springframework.org/schema/util");
                    put("webflow-config", "http://www.springframework.org/schema/webflow-config");
                    put("xsl", "http://www.w3.org/1999/XSL/Transform");
                }
            };
    private final Parameters _exportPackageParameters = new Parameters();
    private final File _file;
    private final Set<String> _ignoredResourcePaths = SetUtil.fromArray(PropsValues.MODULE_FRAMEWORK_WEB_GENERATOR_EXCLUDED_PATHS);
    private final Parameters _importPackageParameters = new Parameters();
    private final Map<String, String[]> _parameters;
    private String _bundleVersion;
    private String _context;
    private File _pluginDir;
    private PluginPackage _pluginPackage;
    private String _servicePackageName;

    public WabProcessor(ClassLoader classLoader, File file, Map<String, String[]> parameters) {

        ToolDependencies.wire();

        _file = file;
        _parameters = parameters;
    }

    public File getProcessedFile() throws IOException {
        _pluginDir = autoDeploy();

        if ((_pluginDir == null) || !_pluginDir.exists() || !_pluginDir.isDirectory()) {

            return null;
        }

        File outputFile = null;

        try (Jar jar = new Jar(_pluginDir)) {
            if (jar.getBsn() == null) {
                outputFile = transformToOSGiBundle(jar);
            }
        } catch (Exception e) {
            ReflectionUtil.throwException(e);
        }

        writeGeneratedWab(outputFile);

        return outputFile;
    }

    private void appendProperty(Analyzer analyzer, String property, String string) {

        analyzer.setProperty(property, Analyzer.append(analyzer.getProperty(property), string));
    }

    private File autoDeploy() {
        String webContextpath = getWebContextPath();

        AutoDeploymentContext autoDeploymentContext = buildAutoDeploymentContext(webContextpath);

        _pluginPackage = autoDeploymentContext.getPluginPackage();

        if (_pluginPackage != null) {
            _context = _pluginPackage.getContext();
        } else {
            _context = autoDeploymentContext.getContext();
        }

        File deployDir = autoDeploymentContext.getDeployDir();

        if (!deployDir.exists()) {
            File parentFile = deployDir.getParentFile();

            deployDir.mkdirs();

            try (Jar jar = new Jar(_file)) {
                jar.expand(deployDir);
            } catch (Exception e) {
                ReflectionUtil.throwException(e);
            }

            try {
                BaseDeployer baseDeployer = new BaseDeployer();

                baseDeployer.setBaseDir(parentFile.getAbsolutePath());
                baseDeployer.setAppServerType(ServerDetector.TOMCAT_ID);
                baseDeployer.setJars(new ArrayList<>());
                baseDeployer.setUnpackWar(true);

                setTlds(baseDeployer);
                setJars(baseDeployer);

                baseDeployer.deployFile(autoDeploymentContext);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        return deployDir;
    }

    private void setTlds(final BaseDeployer baseDeployer) {
        String PREFIX = "E:\\liferay71\\bundles\\tomcat-9.0.6\\webapps\\ROOT\\WEB-INF\\tld\\";

        baseDeployer.setAuiTaglibDTD(PREFIX + "liferay-aui.tld");
        baseDeployer.setPortletTaglibDTD(PREFIX + "liferay-portlet_2_0.tld");
        baseDeployer.setPortletExtTaglibDTD(PREFIX + "liferay-portlet-ext.tld");
        baseDeployer.setSecurityTaglibDTD(PREFIX + "liferay-security.tld");
        baseDeployer.setThemeTaglibDTD(PREFIX + "liferay-theme.tld");
        baseDeployer.setUiTaglibDTD(PREFIX + "liferay-ui.tld");
        baseDeployer.setUtilTaglibDTD(PREFIX + "liferay-util.tld");
    }

    private void setJars(final BaseDeployer baseDeployer) throws Exception {
        List<String> jars = new ArrayList<>();

        baseDeployer.addExtJar(jars, "ext-util-bridges.jar");
        baseDeployer.addExtJar(jars, "ext-util-java.jar");
        baseDeployer.addExtJar(jars, "ext-util-taglib.jar");
        baseDeployer.addRequiredJar(jars, "util-bridges.jar");
        baseDeployer.addRequiredJar(jars, "util-java.jar");
        baseDeployer.addRequiredJar(jars, "util-taglib.jar");
        baseDeployer.setJars(jars);
    }

    private AutoDeploymentContext buildAutoDeploymentContext(String context) {
        AutoDeploymentContext autoDeploymentContext = new AutoDeploymentContext();

        autoDeploymentContext.setContext(context);
        autoDeploymentContext.setFile(_file);

        if (_file.isDirectory()) {
            autoDeploymentContext.setDestDir(_file.getAbsolutePath());

            return autoDeploymentContext;
        }

        File file = new File(_file.getParentFile(), "deploy");

        file.mkdirs();

        autoDeploymentContext.setDestDir(file.getAbsolutePath());

        return autoDeploymentContext;
    }

    private void formatDocument(File file, Document document) throws IOException {
        try {
            FileUtil.write(file, document.formattedString("  "));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private Properties getPluginPackageProperties() {
        File file = new File(_pluginDir, "WEB-INF/liferay-plugin-package.properties");

        if (!file.exists()) {
            return new Properties();
        }

        try {
            return PropertiesUtil.load(FileUtil.read(file));
        } catch (IOException ioe) {
            return new Properties();
        }
    }

    private String getVersionedServicePackageName(String partialPackageName) {
        return StringBundler.concat(_servicePackageName, partialPackageName, ";version=", _bundleVersion);
    }

    private String getWebContextPath() {
        String webContextpath = MapUtil.getString(_parameters, "Web-ContextPath");

        if (!webContextpath.startsWith(StringPool.SLASH)) {
            webContextpath = StringPool.SLASH.concat(webContextpath);
        }

        return webContextpath;
    }

    private void processBeans(Builder analyzer) {
        String beansXMLFile = "WEB-INF/beans.xml";

        File file = new File(_pluginDir, beansXMLFile);

        if (!file.exists()) {
            beansXMLFile = "WEB-INF/classes/META-INF/beans.xml";

            file = new File(_pluginDir, beansXMLFile);
        }

        if (!file.exists()) {
            return;
        }

        String cdiInstruction = analyzer.getProperty(Constants.CDIANNOTATIONS);

        if (Validator.isNotNull(cdiInstruction)) {
            return;
        }

        String finalBeansXMLFile = beansXMLFile;

        Document document = readDocument(file);

        cdiInstruction = "*;discover=all";

        if (document.hasContent()) {
            Element rootElement = document.getRootElement();

            // bean-discovery-mode="all" version="1.1"

            XPath xPath = SAXReaderUtil.createXPath("/cdi-beans:beans/@version", _xsds);

            Node versionNode = xPath.selectSingleNode(rootElement);

            if (versionNode != null) {
                Version version = Version.valueOf(versionNode.getStringValue());

                if (_CDI_ARCHIVE_VERSION.compareTo(version) <= 0) {
                    xPath = SAXReaderUtil.createXPath("/cdi-beans:beans/@bean-discovery-mode", _xsds);

                    Node beanDiscoveryModeNode = xPath.selectSingleNode(
                            rootElement);

                    if (beanDiscoveryModeNode == null) {
                        cdiInstruction = "*;discover=annotated";
                    } else {
                        cdiInstruction = "*;discover=" + beanDiscoveryModeNode.getStringValue();
                    }
                }
            }
        }

        analyzer.setProperty(Constants.CDIANNOTATIONS, cdiInstruction);

        appendProperty(analyzer, Constants.REQUIRE_CAPABILITY, _CDI_REQUIREMENTS);

        Set<Object> plugins = analyzer.getPlugins();

        plugins.add(
                (VerifierPlugin) analyzer1 -> {
                    Parameters requireCapabilities = analyzer1.parseHeader(analyzer1.getProperty(Constants.REQUIRE_CAPABILITY));

                    Map<String, Object> arguments = new HashMap<>();

                    arguments.put("osgi.extender", "osgi.cdi");
                    arguments.put("version", new Version(1));

                    for (Map.Entry<String, Attrs> entry : requireCapabilities.entrySet()) {

                        String namespace = entry.getKey();

                        Attrs attrs = entry.getValue();

                        String filterString = attrs.get(Constants.FILTER_DIRECTIVE);

                        Filter filter = new Filter(filterString);

                        if ("osgi.extender".equals(namespace) && filter.matchMap(arguments)) {

                            attrs.putTyped("descriptor", Collections.singletonList(finalBeansXMLFile));
                        }
                    }

                    analyzer1.setProperty(
                            Constants.REQUIRE_CAPABILITY,
                            requireCapabilities.toString());
                });
    }

    private void processBundleClasspath(Analyzer analyzer, Properties pluginPackageProperties)
            throws IOException {

        appendProperty(analyzer, Constants.BUNDLE_CLASSPATH, "ext/WEB-INF/classes");

        // Class path order is critical

        Map<String, File> classPath = new LinkedHashMap<>();

        classPath.put("WEB-INF/classes", new File(_pluginDir, "WEB-INF/classes"));

        appendProperty(analyzer, Constants.BUNDLE_CLASSPATH, "WEB-INF/classes");

        processFiles(classPath, analyzer);

        Collection<File> files = classPath.values();

        analyzer.setClasspath(files.toArray(new File[classPath.size()]));
    }

    private void processBundleManifestVersion(Analyzer analyzer) {
        String bundleManifestVersion = MapUtil.getString(_parameters, Constants.BUNDLE_MANIFESTVERSION);

        if (Validator.isNull(bundleManifestVersion)) {
            bundleManifestVersion = "2";
        }

        analyzer.setProperty(Constants.BUNDLE_MANIFESTVERSION, bundleManifestVersion);
    }

    private void processBundleSymbolicName(Analyzer analyzer) {
        String bundleSymbolicName = MapUtil.getString(_parameters, Constants.BUNDLE_SYMBOLICNAME);

        if (Validator.isNull(bundleSymbolicName)) {
            bundleSymbolicName = _context.substring(1);
        }

        analyzer.setProperty(Constants.BUNDLE_SYMBOLICNAME, bundleSymbolicName);
    }

    private void processBundleVersion(Analyzer analyzer) {
        _bundleVersion = MapUtil.getString(_parameters, Constants.BUNDLE_VERSION);

        if (Validator.isNull(_bundleVersion)) {
            if (_pluginPackage != null) {
                _bundleVersion = _pluginPackage.getVersion();
            } else {
                _bundleVersion = "1.0.0";
            }
        }

        if (!Version.isVersion(_bundleVersion)) {

            // Convert from the Maven format to the OSGi format

            Matcher matcher = _versionMavenPattern.matcher(_bundleVersion);

            if (matcher.matches()) {
                StringBuilder sb = new StringBuilder();

                sb.append(matcher.group(1));
                sb.append(".");
                sb.append(matcher.group(3));
                sb.append(".");
                sb.append(matcher.group(5));
                sb.append(".");
                sb.append(matcher.group(7));

                _bundleVersion = sb.toString();
            } else {
                _bundleVersion = "0.0.0." + _bundleVersion.replace(".", "_");
            }
        }

        analyzer.setProperty(Constants.BUNDLE_VERSION, _bundleVersion);
    }

    private void processClass(Analyzer analyzer, String value) {
        int index = value.lastIndexOf('.');

        if (index == -1) {
            return;
        }

        Packages packages = analyzer.getReferred();

        String packageName = value.substring(0, index);

        Descriptors.PackageRef packageRef = analyzer.getPackageRef(packageName);

        packages.put(packageRef, new Attrs());
    }

    private void processDeclarativeReferences(Analyzer analyzer) throws IOException {

        processDefaultServletPackages();
        processTLDDependencies(analyzer);

        processPortalListenerClassesDependencies(analyzer);

        Path pluginPath = _pluginDir.toPath();

        processXMLDependencies(analyzer, "WEB-INF/liferay-hook.xml", _XPATHS_HOOK);
        processXMLDependencies(analyzer, "WEB-INF/liferay-portlet.xml", _XPATHS_LIFERAY);
        processXMLDependencies(analyzer, "WEB-INF/portlet.xml", _XPATHS_PORTLET);
        processXMLDependencies(analyzer, "WEB-INF/web.xml", _XPATHS_JAVAEE);

        Path classes = pluginPath.resolve("WEB-INF/classes/");

        processPropertiesDependencies(analyzer, classes, ".properties", _KNOWN_PROPERTY_KEYS);
        processXMLDependencies(analyzer, classes, ".xml", _XPATHS_HBM);
        processXMLDependencies(analyzer, classes, ".xml", _XPATHS_SPRING);
    }

    private void processDefaultServletPackages() {
        for (String value : PropsValues.MODULE_FRAMEWORK_WEB_GENERATOR_DEFAULT_SERVLET_PACKAGES) {

            Parameters defaultPackage = new Parameters(value);

            for (String packageName : defaultPackage.keySet()) {
                if (_importPackageParameters.containsKey(packageName)) {
                    continue;
                }

                _importPackageParameters.add(packageName, _optionalAttrs);
            }
        }
    }

    private void processExportPackageNames(Analyzer analyzer) {
        analyzer.setProperty(Constants.EXPORT_CONTENTS, _exportPackageParameters.toString());
    }

    private void processExtraHeaders(Analyzer analyzer) {
        String bundleSymbolicName = analyzer.getProperty(Constants.BUNDLE_SYMBOLICNAME);

        Properties properties = PropsUtil.getProperties(PropsKeys.MODULE_FRAMEWORK_WEB_GENERATOR_HEADERS, true);

        Enumeration<Object> keys = properties.keys();

        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();

            String value = properties.getProperty(key);

            String processedKey = key;

            if (processedKey.endsWith(StringPool.CLOSE_BRACKET)) {
                String filterString = StringPool.OPEN_BRACKET + bundleSymbolicName + StringPool.CLOSE_BRACKET;

                if (!processedKey.endsWith(filterString)) {
                    continue;
                }

                processedKey = processedKey.substring(0, processedKey.indexOf(StringPool.OPEN_BRACKET));
            }

            if (Validator.isNotNull(value)) {
                Parameters parameters = new Parameters(value);

                if (processedKey.equals(Constants.EXPORT_PACKAGE)) {
                    _exportPackageParameters.mergeWith(parameters, true);
                } else if (processedKey.equals(Constants.IMPORT_PACKAGE)) {
                    _importPackageParameters.mergeWith(parameters, true);
                }

                analyzer.setProperty(processedKey, parameters.toString());
            }
        }
    }

    private void processExtraRequirements() {
        Attrs attrs = new Attrs(_optionalAttrs);

        attrs.put("x-liferay-compatibility:", "spring");

        _importPackageParameters.add("org.eclipse.core.runtime", attrs);

        _importPackageParameters.add("!junit.*", new Attrs());
    }

    private void processFiles(Map<String, File> classPath, Analyzer analyzer) {
        Jar jar = analyzer.getJar();

        Map<String, Resource> resources = jar.getResources();

        Set<Map.Entry<String, Resource>> entrySet = resources.entrySet();

        Iterator<Map.Entry<String, Resource>> iterator = entrySet.iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Resource> entry = iterator.next();

            String path = entry.getKey();

            if (path.equals("WEB-INF/service.xml")) {
                processServicePackageName(entry.getValue());
            } else if (path.startsWith("WEB-INF/lib/")) {

                // Remove any other "-service.jar" or ignored jar so that real
                // imports are used

                if ((path.endsWith("-service.jar") && !path.endsWith(_context.concat("-service.jar"))) || _ignoredResourcePaths.contains(path)) {

                    iterator.remove();

                    continue;
                }

                Resource resource = entry.getValue();

                if (resource instanceof FileResource) {
                    FileResource fileResource = (FileResource) resource;

                    classPath.put(path, fileResource.getFile());

                    appendProperty(analyzer, Constants.BUNDLE_CLASSPATH, path);
                }
            } else if (_ignoredResourcePaths.contains(path)) {
                iterator.remove();
            }
        }
    }

    private void processImportPackageNames(Analyzer analyzer) {
        String packageName = MapUtil.getString(_parameters, Constants.IMPORT_PACKAGE);

        if (Validator.isNotNull(packageName)) {
            analyzer.setProperty(Constants.IMPORT_PACKAGE, packageName);
        } else {
            StringBundler sb = new StringBundler((_importPackageParameters.size() * 4) + 1);

            for (Map.Entry<String, Attrs> entry :
                    _importPackageParameters.entrySet()) {

                String importPackageName = entry.getKey();

                boolean containedInClasspath = false;

                for (Jar jar : analyzer.getClasspath()) {
                    List<String> packages = jar.getPackages();

                    if (packages.contains(importPackageName)) {
                        containedInClasspath = true;

                        break;
                    }
                }

                if (containedInClasspath) {
                    continue;
                }

                sb.append(importPackageName);

                Attrs attrs = entry.getValue();

                if (!attrs.isEmpty()) {
                    sb.append(";");
                    sb.append(entry.getValue());
                }

                sb.append(StringPool.COMMA);
            }

            sb.append("*;resolution:=\"optional\"");

            analyzer.setProperty(Constants.IMPORT_PACKAGE, sb.toString());
        }
    }

    private void processLiferayPortletXML() throws IOException {
        File file = new File(_pluginDir, "WEB-INF/liferay-portlet.xml");

        if (!file.exists()) {
            return;
        }

        Document document = readDocument(file);

        Element rootElement = document.getRootElement();

        for (Element element : rootElement.elements("portlet")) {
            Element strutsPathElement = element.element("struts-path");

            if (strutsPathElement == null) {
                continue;
            }

            String strutsPath = strutsPathElement.getTextTrim();

            if (!strutsPath.startsWith(StringPool.SLASH)) {
                strutsPath = StringPool.SLASH.concat(strutsPath);
            }

            strutsPathElement.setText(
                    Portal.PATH_MODULE.substring(1) + _context + strutsPath);
        }

        formatDocument(file, document);
    }

    private void processPackageNames(Analyzer analyzer) {
        processExportPackageNames(analyzer);
        processImportPackageNames(analyzer);
    }

    private void processPluginPackagePropertiesExportImportPackages(
            Properties pluginPackageProperties) {

        if (pluginPackageProperties == null) {
            return;
        }

        String exportPackage = pluginPackageProperties.getProperty(
                Constants.EXPORT_PACKAGE);

        if (Validator.isNotNull(exportPackage)) {
            Parameters parameters = new Parameters(exportPackage);

            _exportPackageParameters.mergeWith(parameters, true);

            pluginPackageProperties.remove(Constants.EXPORT_PACKAGE);
        }

        String importPackage = pluginPackageProperties.getProperty(
                Constants.IMPORT_PACKAGE);

        if (Validator.isNotNull(importPackage)) {
            Parameters parameters = new Parameters(importPackage);

            _importPackageParameters.mergeWith(parameters, true);

            pluginPackageProperties.remove(Constants.IMPORT_PACKAGE);
        }
    }

    private void processPortalListenerClassesDependencies(Analyzer analyzer) {
        File file = new File(_pluginDir, "WEB-INF/web.xml");

        if (!file.exists()) {
            return;
        }

        Document document = readDocument(file);

        Element rootElement = document.getRootElement();

        List<Element> contextParamElements = rootElement.elements("context-param");

        for (Element contextParamElement : contextParamElements) {
            String paramName = contextParamElement.elementText("param-name");

            if (Validator.isNotNull(paramName) && paramName.equals("portalListenerClasses")) {

                String paramValue = contextParamElement.elementText("param-value");

                String[] portalListenerClassNames = StringUtil.split(paramValue, StringPool.COMMA);

                for (String portalListenerClassName : portalListenerClassNames) {

                    processClass(analyzer, portalListenerClassName.trim());
                }
            }
        }
    }

    private void processPropertiesDependencies(Analyzer analyzer, File file, String[] knownPropertyKeys) {

        if (!file.exists()) {
            return;
        }

        try (InputStream inputStream = new FileInputStream(file)) {
            Properties properties = new Properties();

            properties.load(inputStream);

            if (properties.isEmpty()) {
                return;
            }

            for (String key : knownPropertyKeys) {
                String value = properties.getProperty(key);

                if (value == null) {
                    continue;
                }

                value = value.trim();

                processClass(analyzer, value);
            }
        } catch (Exception e) {

            // Ignore this case

        }
    }

    private void processPropertiesDependencies(Analyzer analyzer, Path path, String suffix, String[] knownPropertyKeys) throws IOException {

        File file = path.toFile();

        if (!file.isDirectory()) {
            return;
        }

        Stream<Path> pathStream = Files.walk(path);

        Stream<File> fileStream = pathStream.map(Path::toFile);

        fileStream.forEach(
                entry -> {
                    String pathString = entry.getPath();

                    if (pathString.endsWith(suffix)) {
                        processPropertiesDependencies(
                                analyzer, entry, knownPropertyKeys);
                    }
                });
    }

    private void processRequiredDeploymentContexts(Analyzer analyzer) {
        if (_pluginPackage == null) {
            return;
        }

        List<String> requiredDeploymentContexts = _pluginPackage.getRequiredDeploymentContexts();

        if (ListUtil.isEmpty(requiredDeploymentContexts)) {
            return;
        }

        StringBundler sb = new StringBundler((6 * requiredDeploymentContexts.size()) - 1);

        for (int i = 0; i < requiredDeploymentContexts.size(); i++) {
            String requiredDeploymentContext = requiredDeploymentContexts.get(i);

            sb.append(requiredDeploymentContext);

            sb.append(StringPool.SEMICOLON);
            sb.append(Constants.BUNDLE_VERSION_ATTRIBUTE);
            sb.append(StringPool.EQUAL);
            sb.append(_bundleVersion);

            if ((i + 1) < requiredDeploymentContexts.size()) {
                sb.append(StringPool.COMMA);
            }
        }

        analyzer.setProperty(Constants.REQUIRE_BUNDLE, sb.toString());
    }

    private void processServicePackageName(Resource resource) {
        try (InputStream inputStream = resource.openInputStream()) {
            Document document = UnsecureSAXReaderUtil.read(inputStream);

            Element rootElement = document.getRootElement();

            _servicePackageName = rootElement.attributeValue("package-path");

            String[] partialPackageNames = {
                    "", ".exception", ".model", ".model.impl", ".service",
                    ".service.base", ".service.http", ".service.impl",
                    ".service.persistence", ".service.persistence.impl"
            };

            for (String partialPackageName : partialPackageNames) {
                Parameters parameters = new Parameters(
                        getVersionedServicePackageName(partialPackageName));

                _exportPackageParameters.mergeWith(parameters, false);
                _importPackageParameters.mergeWith(parameters, false);
            }

            _importPackageParameters.add(
                    "com.liferay.portal.osgi.web.wab.generator", _optionalAttrs);
        } catch (Exception e) {
            _log.error(e, e);
        }
    }

    private void processTLDDependencies(Analyzer analyzer)
            throws IOException {

        File dir = new File(_pluginDir, "WEB-INF/tld");

        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles(
                (File file) -> {
                    if (!file.isFile()) {
                        return false;
                    }

                    String fileName = file.getName();

                    return fileName.endsWith(".tld");
                });

        for (File file : files) {
            String content = FileUtil.read(file);

            Matcher matcher = _tldPackagesPattern.matcher(content);

            while (matcher.find()) {
                String value = matcher.group(1);

                value = value.trim();

                processClass(analyzer, value);
            }
        }
    }

    private void processWebXML(
            Element element, List<Element> initParamElements, Class<?> clazz) {

        if (element == null) {
            return;
        }

        String elementText = element.getTextTrim();

        if (!elementText.equals(clazz.getName())) {
            return;
        }

        for (Element initParamElement : initParamElements) {
            Element paramNameElement = initParamElement.element("param-name");

            String paramNameValue = paramNameElement.getTextTrim();

            if (!paramNameValue.equals(element.getName())) {
                continue;
            }

            Element paramValueElement = initParamElement.element("param-value");

            element.setText(paramValueElement.getTextTrim());

            initParamElement.detach();

            return;
        }
    }

    private void processWebXML(String path) throws IOException {
        File file = new File(_pluginDir, path);

        if (!file.exists()) {
            return;
        }

        Document document = readDocument(file);

        Element rootElement = document.getRootElement();

        for (Element element : rootElement.elements("filter")) {
            Element filterClassElement = element.element("filter-class");

            processWebXML(filterClassElement, element.elements("init-param"), PortalClassLoaderFilter.class);
        }

        for (Element element : rootElement.elements("servlet")) {
            Element servletClassElement = element.element("servlet-class");

            processWebXML(servletClassElement, element.elements("init-param"), PortalClassLoaderServlet.class);
        }

        formatDocument(file, document);
    }

    private void processXMLDependencies(
            Analyzer analyzer, File file, String xPathExpression) {

        if (!file.exists()) {
            return;
        }

        Document document = readDocument(file);

        if (!document.hasContent()) {
            return;
        }

        Element rootElement = document.getRootElement();

        XPath xPath = SAXReaderUtil.createXPath(xPathExpression, _xsds);

        List<Node> nodes = xPath.selectNodes(rootElement);

        for (Node node : nodes) {
            String text = node.getText();

            text = text.trim();

            processClass(analyzer, text);
        }
    }

    private void processXMLDependencies(Analyzer analyzer, Path path, String suffix, String xPathExpression)
            throws IOException {

        File file = path.toFile();

        if (!file.isDirectory()) {
            return;
        }

        Stream<Path> pathStream = Files.walk(path);

        Stream<File> fileStream = pathStream.map(Path::toFile);

        fileStream.forEach(
                entry -> {
                    String pathString = entry.getPath();

                    if (pathString.endsWith(suffix)) {
                        processXMLDependencies(analyzer, entry, _XPATHS_SPRING);
                    }
                });
    }

    private void processXMLDependencies(
            Analyzer analyzer, String fileName, String xPathExpression) {

        File file = new File(_pluginDir, fileName);

        processXMLDependencies(analyzer, file, xPathExpression);
    }

    private Document readDocument(File file) {
        try {
            String content = FileUtil.read(file);

            return UnsecureSAXReaderUtil.read(content);
        } catch (Exception de) {
            return SAXReaderUtil.createDocument();
        }
    }

    private File transformToOSGiBundle(Jar jar) throws IOException {
        Builder analyzer = new Builder();

        analyzer.setBase(_pluginDir);
        analyzer.setJar(jar);
        analyzer.setProperty("-jsp", "*.jsp,*.jspf");
        analyzer.setProperty("Web-ContextPath", getWebContextPath());

        Set<Object> plugins = analyzer.getPlugins();

        Object dsAnnotationsPlugin = null;

        for (Object plugin : plugins) {
            if (plugin instanceof DSAnnotations) {
                dsAnnotationsPlugin = plugin;
            }
        }

        if (dsAnnotationsPlugin != null) {
            plugins.remove(dsAnnotationsPlugin);
        }

        plugins.add(new JspAnalyzerPlugin());

        Properties pluginPackageProperties = getPluginPackageProperties();

        processBundleVersion(analyzer);
        processBundleClasspath(analyzer, pluginPackageProperties);
        processBundleSymbolicName(analyzer);
        processExtraHeaders(analyzer);
        processPluginPackagePropertiesExportImportPackages(pluginPackageProperties);

        processBundleManifestVersion(analyzer);

        processLiferayPortletXML();
        processWebXML("WEB-INF/web.xml");
        processWebXML("WEB-INF/liferay-web.xml");

        processDeclarativeReferences(analyzer);

        processExtraRequirements();

        processPackageNames(analyzer);

        processRequiredDeploymentContexts(analyzer);

        processBeans(analyzer);

        _processExcludedJSPs(analyzer);

        analyzer.setProperties(pluginPackageProperties);

        try {
            jar = analyzer.build();

            File outputFile = analyzer.getOutputFile(null);

            jar.write(outputFile);

            return outputFile;
        } catch (Exception e) {
            throw new IOException("Unable to calculate the manifest", e);
        } finally {
            analyzer.close();
        }
    }

    private void writeGeneratedWab(File file) throws IOException {
        File dir = new File(PropsValues.MODULE_FRAMEWORK_WEB_GENERATOR_GENERATED_WABS_STORE_DIR);

        dir.mkdirs();

        StringBundler sb = new StringBundler(5);

        String name = _file.getName();

        sb.append(name.substring(0, name.lastIndexOf(StringPool.PERIOD)));

//        sb.append(StringPool.DASH);
//
//        Format format = FastDateFormatFactoryUtil.getSimpleDateFormat(
//                PropsValues.INDEX_DATE_FORMAT_PATTERN);
//
//        sb.append(format.format(new Date()));

        sb.append(".wab.");

        sb.append(FileUtil.getExtension(name));

        FileUtil.copyFile(file, new File(dir, sb.toString()));

        System.out.println(new File(dir, sb.toString()));
    }

    private void _processExcludedJSPs(Analyzer analyzer) {
        File file = new File(_pluginDir, "/WEB-INF/liferay-hook.xml");

        if (!file.exists()) {
            return;
        }

        Document document = readDocument(file);

        if (!document.hasContent()) {
            return;
        }

        Element rootElement = document.getRootElement();

        List<Node> nodes = rootElement.selectNodes("//custom-jsp-dir");

        String value = analyzer.getProperty("-jsp");

        for (Node node : nodes) {
            String text = node.getText();

            if (text.startsWith("/")) {
                text = text.substring(1);
            }

            value = StringBundler.concat("!", text, "/*,", value);
        }

        analyzer.setProperty("-jsp", value);
    }

}