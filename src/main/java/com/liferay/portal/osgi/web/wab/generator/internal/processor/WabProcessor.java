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
import com.liferay.portal.kernel.plugin.PluginPackage;
import com.liferay.portal.kernel.servlet.PortalClassLoaderFilter;
import com.liferay.portal.kernel.servlet.PortalClassLoaderServlet;
import com.liferay.portal.kernel.util.*;
import com.liferay.portal.kernel.xml.*;
import com.liferay.portal.osgi.web.wab.generator.internal.helper.DeployerHelper;

import com.liferay.portal.tools.ToolDependencies;
import com.liferay.portal.tools.deploy.BaseDeployer;
import com.liferay.portal.util.PropsValues;
import com.liferay.util.ant.DeleteTask;
import com.liferay.whip.util.ReflectionUtil;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static be.gfi.helper.TimerHelper.timer;
import static com.liferay.portal.osgi.web.wab.generator.internal.processor.Constants.*;

/**
 * @author Raymond Augé
 * @author Miguel Pastor
 */
public class WabProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(WabProcessor.class);

    private static final String WEB_INF_WEB_XML = "WEB-INF/web.xml";
    private static final String WEB_INF_LIFERAY_WEB_XML = "WEB-INF/liferay-web.xml";
    private static final String WEB_CONTEXT_PATH = "Web-ContextPath";
    private static final String WEB_INF_LIFERAY_PLUGIN_PACKAGE_PROPERTIES = "WEB-INF/liferay-plugin-package.properties";
    private static final String EXT_WEB_INF_CLASSES = "ext/WEB-INF/classes";
    private static final String WEB_INF_CLASSES = "WEB-INF/classes";
    private static final String WEB_INF_LIFERAY_PORTLET_XML = "WEB-INF/liferay-portlet.xml";
    private static final String PORTLET = "portlet";
    private static final String STRUTS_PATH = "struts-path";
    private static final String FILTER = "filter";
    private static final String FILTER_CLASS = "filter-class";
    private static final String INIT_PARAM = "init-param";
    private static final String SERVLET = "servlet";
    private static final String SERVLET_CLASS = "servlet-class";
    private static final String PARAM_NAME = "param-name";
    private static final String PARAM_VALUE = "param-value";
    private static final String WEB_INF_LIFERAY_HOOK_XML = "WEB-INF/liferay-hook.xml";
    private static final String WEB_INF_PORTLET_XML = "WEB-INF/portlet.xml";
    private static final String PROPERTIES_EXT = ".properties";
    private static final String XML_EXT = ".xml";
    private static final String WEB_INF_TLD = "WEB-INF/tld";
    private static final String TLD_EXT = ".tld";
    private static final String CONTEXT_PARAM = "context-param";
    private static final String PORTAL_LISTENER_CLASSES = "portalListenerClasses";
    private static final String WEB_INF_BEANS_XML = "WEB-INF/beans.xml";
    private static final String WEB_INF_CLASSES_META_INF_BEANS_XML = "WEB-INF/classes/META-INF/beans.xml";
    private static final String OSGI_EXTENDER = "osgi.extender";
    private static final String OSGI_CDI = "osgi.cdi";
    private static final String VERSION = "version";
    private static final String DESCRIPTOR = "descriptor";
    private static final String WEB_INF_LIFERAY_HOOK_XML1 = "/WEB-INF/liferay-hook.xml";
    private static final String WEB_INF_SERVICE_XML = "WEB-INF/service.xml";
    private static final String WEB_INF_LIB = "WEB-INF/lib/";
    private static final String PACKAGE_PATH = "package-path";
    private static final String WAB_EXT = ".wab";
    private static final String DEPLOY_DIR = "DEPLOY_DIR";
    private static final String DEPLOY = "deploy";

    private final Parameters _exportPackageParameters = new Parameters();
    private final File _file;
    private final Set<String> _ignoredResourcePaths = SetUtil.fromArray(PropsValues.MODULE_FRAMEWORK_WEB_GENERATOR_EXCLUDED_PATHS);
    private final Parameters _importPackageParameters = new Parameters();
    private final Map<String, String[]> _parameters;
    private String _bundleVersion;
    private String _context;
    private File _pluginDir;
    private Optional<PluginPackage> _pluginPackage;
    private String _servicePackageName;

    public WabProcessor(File file, Map<String, String[]> parameters) {
        ToolDependencies.wire();

        _file = file;
        _parameters = parameters;
    }

    public Optional<File> getProcessedFile() throws IOException {
        _pluginDir = autoDeploy();

        if (!_pluginDir.exists() || !_pluginDir.isDirectory()) {
            return Optional.empty();
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

        return Optional.ofNullable(outputFile);
    }

    private File autoDeploy() {
        String webContextpath = getWebContextPath();

        AutoDeploymentContext autoDeploymentContext = buildAutoDeploymentContext(webContextpath);

        _pluginPackage = Optional.ofNullable(autoDeploymentContext.getPluginPackage());

        _context = _pluginPackage.map(PluginPackage::getContext).orElse(autoDeploymentContext.getContext());

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
                BaseDeployer baseDeployer = DeployerHelper.getBaseDeployer(parentFile, _parameters);

                baseDeployer.deployFile(autoDeploymentContext);
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }

        return deployDir;
    }

    private AutoDeploymentContext buildAutoDeploymentContext(String context) {
        AutoDeploymentContext autoDeploymentContext = new AutoDeploymentContext();

        autoDeploymentContext.setContext(context);
        autoDeploymentContext.setFile(_file);

        if (_file.isDirectory()) {
            autoDeploymentContext.setDestDir(_file.getAbsolutePath());

            return autoDeploymentContext;
        }

        File file = new File(_file.getParentFile(), DEPLOY);

        if (file.exists()) {
            DeleteTask.deleteDirectory(file);
        }

        file.mkdirs();

        autoDeploymentContext.setDestDir(file.getAbsolutePath());

        return autoDeploymentContext;
    }

    private File transformToOSGiBundle(final Jar jar) throws IOException {
        try (final Builder analyzer = new Builder()) {

            analyzer.setBase(_pluginDir);
            analyzer.setJar(jar);
            analyzer.setProperty("-jsp", "*.jsp,*.jspf");
            analyzer.setProperty(WEB_CONTEXT_PATH, getWebContextPath());

            final Set<Object> plugins = analyzer.getPlugins();

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

            final Properties pluginPackageProperties = getPluginPackageProperties();

            timer.time("processBundleVersion", () -> processBundleVersion(analyzer));
            timer.time("processBundleClasspath", () -> processBundleClasspath(analyzer));
            timer.time("processBundleSymbolicName", () -> processBundleSymbolicName(analyzer));
            timer.time("processExtraHeaders", () -> processExtraHeaders(analyzer));
            timer.time("processPluginPackagePropertiesExportImportPackages", () ->
                    processPluginPackagePropertiesExportImportPackages(pluginPackageProperties));

            timer.time("processBundleManifestVersion", () -> processBundleManifestVersion(analyzer));

            timer.time("processLiferayPortletXML", this::processLiferayPortletXML);
            timer.time("processWebXML", () -> processWebXML(WEB_INF_WEB_XML));
            timer.time("processWebXML", () -> processWebXML(WEB_INF_LIFERAY_WEB_XML));

            timer.time("processDeclarativeReferences", () -> processDeclarativeReferences(analyzer));

            timer.time("processExtraRequirements", this::processExtraRequirements);

            timer.time("processPackageNames", () -> processPackageNames(analyzer));

            timer.time("processRequiredDeploymentContexts", () -> processRequiredDeploymentContexts(analyzer));

            timer.time("processBeans", () -> processBeans(analyzer));

            timer.time("_processExcludedJSPs", () -> _processExcludedJSPs(analyzer));

            analyzer.setProperties(pluginPackageProperties);

            timer.start("analyzer.build");
            final Jar jarWithManifest = analyzer.build();
            timer.end("analyzer.build");

            final File outputFile = analyzer.getOutputFile(null);

            timer.time("jar.write", () -> jarWithManifest.write(outputFile));

            return outputFile;
        } catch (Exception e) {
            throw new IOException("Unable to calculate the manifest", e);
        }
    }

    private String getWebContextPath() {
        String webContextpath = MapUtil.getString(_parameters, WEB_CONTEXT_PATH);

        if (!webContextpath.startsWith(StringPool.SLASH)) {
            webContextpath = StringPool.SLASH.concat(webContextpath);
        }

        return webContextpath;
    }

    private Properties getPluginPackageProperties() {
        File file = new File(_pluginDir, WEB_INF_LIFERAY_PLUGIN_PACKAGE_PROPERTIES);

        if (!file.exists()) {
            return new Properties();
        }

        try {
            return PropertiesUtil.load(FileUtil.read(file));
        } catch (IOException ioe) {
            return new Properties();
        }
    }

    private void processBundleVersion(Analyzer analyzer) {
        _bundleVersion = MapUtil.getString(_parameters, Constants.BUNDLE_VERSION);

        if (Validator.isNull(_bundleVersion)) {
            _bundleVersion = _pluginPackage.map(PluginPackage::getVersion).orElse("1.0.0");
        }

        if (!Version.isVersion(_bundleVersion)) {
            convertFromMavenToOSGiFormat();
        }

        analyzer.setProperty(Constants.BUNDLE_VERSION, _bundleVersion);
    }

    private void convertFromMavenToOSGiFormat() {
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
            _bundleVersion = "0.0.0." + _bundleVersion.replace(StringPool.PERIOD, StringPool.UNDERLINE);
        }
    }

    private void processBundleClasspath(Analyzer analyzer) throws IOException {

        appendProperty(analyzer, Constants.BUNDLE_CLASSPATH, EXT_WEB_INF_CLASSES);

        // Class path order is critical

        Map<String, File> classPath = new LinkedHashMap<>();

        classPath.put(WEB_INF_CLASSES, new File(_pluginDir, WEB_INF_CLASSES));

        appendProperty(analyzer, Constants.BUNDLE_CLASSPATH, WEB_INF_CLASSES);

        processFiles(classPath, analyzer);

        Collection<File> files = classPath.values();

        analyzer.setClasspath(files.toArray(new File[classPath.size()]));
    }

    private void processBundleSymbolicName(Analyzer analyzer) {
        String bundleSymbolicName = MapUtil.getString(_parameters, Constants.BUNDLE_SYMBOLICNAME);

        if (Validator.isNull(bundleSymbolicName)) {
            bundleSymbolicName = _context.substring(1);
        }

        analyzer.setProperty(Constants.BUNDLE_SYMBOLICNAME, bundleSymbolicName);
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

    private void processPluginPackagePropertiesExportImportPackages(Properties pluginPackageProperties) {

        if (pluginPackageProperties == null) {
            return;
        }

        String exportPackage = pluginPackageProperties.getProperty(Constants.EXPORT_PACKAGE);

        if (Validator.isNotNull(exportPackage)) {
            Parameters parameters = new Parameters(exportPackage);

            _exportPackageParameters.mergeWith(parameters, true);

            pluginPackageProperties.remove(Constants.EXPORT_PACKAGE);
        }

        String importPackage = pluginPackageProperties.getProperty(Constants.IMPORT_PACKAGE);

        if (Validator.isNotNull(importPackage)) {
            Parameters parameters = new Parameters(importPackage);

            _importPackageParameters.mergeWith(parameters, true);

            pluginPackageProperties.remove(Constants.IMPORT_PACKAGE);
        }
    }

    private void processBundleManifestVersion(Analyzer analyzer) {
        String bundleManifestVersion = MapUtil.getString(_parameters, Constants.BUNDLE_MANIFESTVERSION);

        if (Validator.isNull(bundleManifestVersion)) {
            bundleManifestVersion = "2";
        }

        analyzer.setProperty(Constants.BUNDLE_MANIFESTVERSION, bundleManifestVersion);
    }

    private void processLiferayPortletXML() throws IOException {
        File file = new File(_pluginDir, WEB_INF_LIFERAY_PORTLET_XML);

        if (!file.exists()) {
            return;
        }

        Document document = readDocument(file);

        Element rootElement = document.getRootElement();

        for (Element element : rootElement.elements(PORTLET)) {
            Element strutsPathElement = element.element(STRUTS_PATH);

            if (strutsPathElement == null) {
                continue;
            }

            String strutsPath = strutsPathElement.getTextTrim();

            if (!strutsPath.startsWith(StringPool.SLASH)) {
                strutsPath = StringPool.SLASH.concat(strutsPath);
            }

            strutsPathElement.setText(Portal.PATH_MODULE.substring(1) + _context + strutsPath);
        }

        formatDocument(file, document);
    }

    private void processWebXML(String path) throws IOException {
        File file = new File(_pluginDir, path);

        if (!file.exists()) {
            return;
        }

        Document document = readDocument(file);

        Element rootElement = document.getRootElement();

        for (Element element : rootElement.elements(FILTER)) {
            Element filterClassElement = element.element(FILTER_CLASS);

            processWebXML(filterClassElement, element.elements(INIT_PARAM), PortalClassLoaderFilter.class);
        }

        for (Element element : rootElement.elements(SERVLET)) {
            Element servletClassElement = element.element(SERVLET_CLASS);

            processWebXML(servletClassElement, element.elements(INIT_PARAM), PortalClassLoaderServlet.class);
        }

        formatDocument(file, document);
    }

    private void processWebXML(Element element, List<Element> initParamElements, Class<?> clazz) {

        if (element == null) {
            return;
        }

        String elementText = element.getTextTrim();

        if (!elementText.equals(clazz.getName())) {
            return;
        }

        for (Element initParamElement : initParamElements) {
            Element paramNameElement = initParamElement.element(PARAM_NAME);

            String paramNameValue = paramNameElement.getTextTrim();

            if (!paramNameValue.equals(element.getName())) {
                continue;
            }

            Element paramValueElement = initParamElement.element(PARAM_VALUE);

            element.setText(paramValueElement.getTextTrim());

            initParamElement.detach();

            return;
        }
    }

    private void processDeclarativeReferences(Analyzer analyzer) throws IOException {

        processDefaultServletPackages();
        processTLDDependencies(analyzer);

        processPortalListenerClassesDependencies(analyzer);

        Path pluginPath = _pluginDir.toPath();

        processXMLDependencies(analyzer, WEB_INF_LIFERAY_HOOK_XML, _XPATHS_HOOK);
        processXMLDependencies(analyzer, WEB_INF_LIFERAY_PORTLET_XML, _XPATHS_LIFERAY);
        processXMLDependencies(analyzer, WEB_INF_PORTLET_XML, _XPATHS_PORTLET);
        processXMLDependencies(analyzer, WEB_INF_WEB_XML, _XPATHS_JAVAEE);

        Path classes = pluginPath.resolve("WEB-INF/classes/");

        processPropertiesDependencies(analyzer, classes, PROPERTIES_EXT, _KNOWN_PROPERTY_KEYS);
        processXMLDependencies(analyzer, classes, XML_EXT);
        processXMLDependencies(analyzer, classes, XML_EXT);
    }

    private void processXMLDependencies(Analyzer analyzer, String fileName, String xPathExpression) {

        File file = new File(_pluginDir, fileName);

        processXMLDependencies(analyzer, file, xPathExpression);
    }

    private void processXMLDependencies(Analyzer analyzer, Path path, String suffix) throws IOException {

        File file = path.toFile();

        if (!file.isDirectory()) {
            return;
        }

        Stream<Path> pathStream = Files.walk(path);

        Stream<File> fileStream = pathStream.map(Path::toFile);

        fileStream.forEach(entry -> {
            String pathString = entry.getPath();

            if (pathString.endsWith(suffix)) {
                processXMLDependencies(analyzer, entry, _XPATHS_SPRING);
            }
        });
    }

    private void processXMLDependencies(Analyzer analyzer, File file, String xPathExpression) {

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

    private void processPropertiesDependencies(Analyzer analyzer, Path path, String suffix, String[] knownPropertyKeys)
            throws IOException {

        File file = path.toFile();

        if (!file.isDirectory()) {
            return;
        }

        Stream<Path> pathStream = Files.walk(path);

        Stream<File> fileStream = pathStream.map(Path::toFile);

        fileStream.forEach(entry -> {
            String pathString = entry.getPath();

            if (pathString.endsWith(suffix)) {
                processPropertiesDependencies(analyzer, entry, knownPropertyKeys);
            }
        });
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

    private void processTLDDependencies(Analyzer analyzer) throws IOException {

        File dir = new File(_pluginDir, WEB_INF_TLD);

        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] files = dir.listFiles((File file) -> {
            if (!file.isFile()) {
                return false;
            }

            String fileName = file.getName();

            return fileName.endsWith(TLD_EXT);
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

    private void processPortalListenerClassesDependencies(Analyzer analyzer) {
        File file = new File(_pluginDir, WEB_INF_WEB_XML);

        if (!file.exists()) {
            return;
        }

        Document document = readDocument(file);

        Element rootElement = document.getRootElement();

        List<Element> contextParamElements = rootElement.elements(CONTEXT_PARAM);

        for (Element contextParamElement : contextParamElements) {
            String paramName = contextParamElement.elementText(PARAM_NAME);

            if (Validator.isNotNull(paramName) && paramName.equals(PORTAL_LISTENER_CLASSES)) {

                String paramValue = contextParamElement.elementText(PARAM_VALUE);

                String[] portalListenerClassNames = StringUtil.split(paramValue, StringPool.COMMA);

                for (String portalListenerClassName : portalListenerClassNames) {
                    processClass(analyzer, portalListenerClassName.trim());
                }
            }
        }
    }

    private void processExtraRequirements() {
        Attrs attrs = new Attrs(_optionalAttrs);

        attrs.put("x-liferay-compatibility:", "spring");

        _importPackageParameters.add("org.eclipse.core.runtime", attrs);

        _importPackageParameters.add("!junit.*", new Attrs());
    }

    private void processPackageNames(Analyzer analyzer) {
        processExportPackageNames(analyzer);
        processImportPackageNames(analyzer);
    }

    private void processExportPackageNames(Analyzer analyzer) {
        analyzer.setProperty(Constants.EXPORT_CONTENTS, _exportPackageParameters.toString());
    }

    private void processImportPackageNames(Analyzer analyzer) {
        String packageName = MapUtil.getString(_parameters, Constants.IMPORT_PACKAGE);

        if (Validator.isNotNull(packageName)) {
            analyzer.setProperty(Constants.IMPORT_PACKAGE, packageName);
        } else {
            StringBundler sb = new StringBundler((_importPackageParameters.size() * 4) + 1);

            for (Map.Entry<String, Attrs> entry : _importPackageParameters.entrySet()) {

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

    private void processRequiredDeploymentContexts(Analyzer analyzer) {
        List<String> requiredDeploymentContexts = _pluginPackage.map(PluginPackage::getRequiredDeploymentContexts)
                .orElse(Collections.emptyList());

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

    private void processBeans(Builder analyzer) {
        String beansXMLFile = WEB_INF_BEANS_XML;

        File file = new File(_pluginDir, beansXMLFile);

        if (!file.exists()) {
            beansXMLFile = WEB_INF_CLASSES_META_INF_BEANS_XML;

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
                    final String requireCapabilityProperty = analyzer1.getProperty(Constants.REQUIRE_CAPABILITY);
                    Parameters requireCapabilities = analyzer1.parseHeader(requireCapabilityProperty);

                    Map<String, Object> arguments = new HashMap<>();

                    arguments.put(OSGI_EXTENDER, OSGI_CDI);
                    arguments.put(VERSION, new Version(1));

                    for (Map.Entry<String, Attrs> entry : requireCapabilities.entrySet()) {

                        String namespace = entry.getKey();

                        Attrs attrs = entry.getValue();

                        String filterString = attrs.get(Constants.FILTER_DIRECTIVE);

                        Filter filter = new Filter(filterString);

                        if (OSGI_EXTENDER.equals(namespace) && filter.matchMap(arguments)) {

                            attrs.putTyped(DESCRIPTOR, Collections.singletonList(finalBeansXMLFile));
                        }
                    }

                    analyzer1.setProperty(Constants.REQUIRE_CAPABILITY, requireCapabilities.toString());
                });
    }

    private void _processExcludedJSPs(Analyzer analyzer) {
        File file = new File(_pluginDir, WEB_INF_LIFERAY_HOOK_XML1);

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

            if (text.startsWith(StringPool.SLASH)) {
                text = text.substring(1);
            }

            value = StringBundler.concat("!", text, "/*,", value);
        }

        analyzer.setProperty("-jsp", value);
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

    private void processFiles(Map<String, File> classPath, Analyzer analyzer) {
        Jar jar = analyzer.getJar();

        Map<String, Resource> resources = jar.getResources();

        Set<Map.Entry<String, Resource>> entrySet = resources.entrySet();

        Iterator<Map.Entry<String, Resource>> iterator = entrySet.iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Resource> entry = iterator.next();

            String path = entry.getKey();

            if (path.equals(WEB_INF_SERVICE_XML)) {
                processServicePackageName(entry.getValue());
            } else if (path.startsWith(WEB_INF_LIB)) {

                // Remove any other "-service.jar" or ignored jar so that real
                // imports are used

                if ((path.endsWith("-service.jar") && !path.endsWith(_context.concat("-service.jar")))
                        || _ignoredResourcePaths.contains(path)) {

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

    private void processServicePackageName(Resource resource) {
        try (InputStream inputStream = resource.openInputStream()) {
            Document document = UnsecureSAXReaderUtil.read(inputStream);

            Element rootElement = document.getRootElement();

            _servicePackageName = rootElement.attributeValue(PACKAGE_PATH);

            String[] partialPackageNames = {
                    "", ".exception", ".model", ".model.impl", ".service",
                    ".service.base", ".service.http", ".service.impl",
                    ".service.persistence", ".service.persistence.impl"
            };

            for (String partialPackageName : partialPackageNames) {
                Parameters parameters = new Parameters(getVersionedServicePackageName(partialPackageName));

                _exportPackageParameters.mergeWith(parameters, false);
                _importPackageParameters.mergeWith(parameters, false);
            }

            _importPackageParameters.add("com.liferay.portal.osgi.web.wab.generator", _optionalAttrs);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private String getVersionedServicePackageName(String partialPackageName) {
        return StringBundler.concat(_servicePackageName, partialPackageName, ";version=", _bundleVersion);
    }

    private void appendProperty(Analyzer analyzer, String property, String string) {
        analyzer.setProperty(property, Analyzer.append(analyzer.getProperty(property), string));
    }

    private void formatDocument(File file, Document document) throws IOException {
        FileUtil.write(file, document.formattedString("  "));
    }

    private Document readDocument(File file) {
        try {
            String content = FileUtil.read(file);

            return UnsecureSAXReaderUtil.read(content);
        } catch (Exception de) {
            return SAXReaderUtil.createDocument();
        }
    }

    private void writeGeneratedWab(File file) throws IOException {
        File dir = new File(PropsValues.MODULE_FRAMEWORK_WEB_GENERATOR_GENERATED_WABS_STORE_DIR);

        dir.mkdirs();

        String name = FilenameUtils.removeExtension(_file.getName()) + WAB_EXT;

        FileUtil.copyFile(file, new File(dir, name));
        LOG.info("WAB created: {}", new File(dir, name));

        String deployPath = MapUtil.getString(_parameters, DEPLOY_DIR);
        if (!deployPath.isEmpty()) {
            LOG.info("Deploying to {}", deployPath);
            File deployDir = new File(deployPath, name);
            FileUtil.copyFile(file, deployDir);
        } else {
            LOG.warn("Deploy folder not configured - manual deploy required");
        }
    }
}
