package com.liferay.portal.osgi.web.wab.generator.internal.processor;

import aQute.bnd.header.Attrs;
import aQute.bnd.version.Version;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;

import java.util.regex.Pattern;

public class Constants {

    public static final Attrs _optionalAttrs = new Attrs() {
        {
            put("resolution:", "optional");
        }
    };

    public static final Pattern _tldPackagesPattern = Pattern.compile(
            "<[^>]+?-class>\\p{Space}*?(.*?)\\p{Space}*?</[^>]+?-class>");

    public static final Pattern _versionMavenPattern = Pattern.compile(
            "(\\d{1,9})(\\.(\\d{1,9})(\\.(\\d{1,9})(-([-_\\da-zA-Z]+))?)?)?");

    public static final Version _CDI_ARCHIVE_VERSION = new Version(1, 1, 0);

    public static final String _CDI_REQUIREMENTS = StringBundler.concat(
            "osgi.cdi.extension;filter:='(osgi.cdi.extension=aries.cdi.http)',",
            "osgi.cdi.extension;filter:='(osgi.cdi.extension=", "com.liferay.bean.portlet.cdi.extension)'");

    public static final String[] _KNOWN_PROPERTY_KEYS = {"jdbc.driverClassName"};

    public static final String _XPATHS_HBM = StringUtil.merge(
            new String[]{"//class/@name", "//id/@access", "//import/@class", "//property/@type"}, "|");

    public static final String _XPATHS_HOOK = StringUtil.merge(
            new String[]{"//indexer-post-processor-impl", "//service-impl", "//servlet-filter-impl", "//struts-action-impl"}, "|");

    public static final String _XPATHS_JAVAEE = StringUtil.merge(
            new String[]{"//j2ee:filter-class", "//j2ee:listener-class", "//j2ee:servlet-class", "//javaee:filter-class",
                    "//javaee:listener-class", "//javaee:servlet-class"},
            "|");

    public static final String _XPATHS_LIFERAY = StringUtil.merge(
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

    public static final String _XPATHS_PORTLET = StringUtil.merge(
            new String[]{"//portlet2:filter-class", "//portlet2:listener-class", "//portlet2:portlet-class", "//portlet2:resource-bundle"}, "|");

    public static final String _XPATHS_SPRING = StringUtil.merge(
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
}
