//package com.liferay.portal.osgi.web.wab.generator;
//
//import java.io.File;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
//import aQute.bnd.osgi.Jar;
//import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;
//import com.liferay.portal.kernel.plugin.PluginPackage;
//import com.liferay.portal.kernel.util.*;
//import com.liferay.portal.osgi.web.wab.generator.internal.processor.WabProcessor;
//import com.liferay.portal.tools.ToolDependencies;
//import com.liferay.portal.tools.deploy.WebDeployer;
//import com.liferay.portal.util.PropsValues;
//import com.liferay.util.ant.DeleteTask;
//import com.liferay.util.ant.ExpandTask;
//import com.liferay.whip.util.ReflectionUtil;
//import org.gradle.internal.FileUtils;
//
//
//public class WabProcessorTask extends WabProcessor {
//
//
//	public WabProcessorTask(File file, Map<String, String[]> parameters) {
//		super(file, parameters);
//
//		ToolDependencies.wire();
//	}
//
//	public File getProcessedFile() throws IOException {
//		File pluginDir = autoDeploy();
//
//		if ((pluginDir == null) || !pluginDir.exists() || !pluginDir.isDirectory()) {
//			return null;
//		}
//
//		File outputFile = null;
//
//		try (Jar jar = new Jar(pluginDir)) {
//			if (jar.getBsn() == null) {
//				System.out.println("Transforming to OSGi bundle");
//
//				outputFile = transformToOSGiBundle(jar, pluginDir);
//
//				System.out.println("Transformation done");
//			}
//		}
//		catch (Exception e) {
//			ReflectionUtil.throwException(e);
//		}
//
//		writeGeneratedWab(outputFile);
//
//		return outputFile;
//	}
//
//	private File autoDeploy() {
//		String webContextpath = this.getWebContextPath();
//		AutoDeploymentContext autoDeploymentContext = this.buildAutoDeploymentContext(webContextpath);
//		File srcFile = autoDeploymentContext.getFile();
//		PluginPackage pluginPackage = autoDeploymentContext.getPluginPackage();
//
//		File destDir = new File("E:\\Git\\liferay-springmvc-sample\\temp");
//		destDir.mkdirs();
//		File mergeDir = new File("E:\\Git\\liferay-springmvc-sample\\merge\\liferay-springmvc-sample.war");
//		File deployDir = new File("E:\\Git\\liferay-springmvc-sample\\deploy\\liferay-springmvc-sample");
//		deployDir.mkdirs();
//
////		try (Jar jar = new Jar(srcFile)) {
////			jar.expand(destDir);
////		} catch (Exception e) {
////			e.printStackTrace();
////		}
//
//		try {
//			WebDeployer deployer = setupBaseDeployer();
//
//			//File deployDirFile = new File(destDir + "/" + srcFile.getName());
//			String displayName = "/" + FileUtils.removeExtension(autoDeploymentContext.getFile().getName());
//			boolean overwrite = false;
//
////			Path tempDirPath = Files.createTempDirectory(Paths.get(SystemProperties.get("java.io.tmpdir")), (String)null);
////			File tempDir = tempDirPath.toFile();
//			ExpandTask.expand(srcFile, destDir);
//
//			deployer.deployDirectory(destDir, mergeDir, deployDir, displayName, overwrite, pluginPackage);
//
//			//DeleteTask.deleteDirectory(tempDir);
//
//			//deployer.deployDirectory(destDir, mergeDir, deployDir, displayName, false, pluginPackage);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//
//		return deployDir;
//	}
//
//	private void writeGeneratedWab(File file) throws IOException {
//		File dir = new File(PropsValues.MODULE_FRAMEWORK_WEB_GENERATOR_GENERATED_WABS_STORE_DIR);
//
//		dir.mkdirs();
//
//		StringBundler sb = new StringBundler(5);
//
//		String name = file.getName();
//
//		sb.append(name);
//
//		sb.append(".wab.war");
//
//		FileUtil.copyFile(file, new File(dir, sb.toString()));
//	}
//
//	private WebDeployer setupBaseDeployer() throws Exception {
//		WebDeployer baseDeployer = new WebDeployer();
//
//		baseDeployer.setAppServerType(ServerDetector.TOMCAT_ID);
//		baseDeployer.setBaseDir(PropsUtil.get(PropsKeys.AUTO_DEPLOY_DEPLOY_DIR));
//		baseDeployer.setUnpackWar(true);
//
//		setTlds(baseDeployer);
//		setJars(baseDeployer);
//
//		return baseDeployer;
//	}
//
//	private void setTlds(final WebDeployer baseDeployer) {
//		String PREFIX = "C:\\liferay-ce-portal-7.0-ga7\\tomcat-8.0.32\\webapps\\ROOT\\WEB-INF\\tld\\";
//
//		baseDeployer.setAuiTaglibDTD(PREFIX + "liferay-aui.tld");
//		baseDeployer.setPortletTaglibDTD("liferay-portlet_2_0.tld");
//		baseDeployer.setPortletExtTaglibDTD("liferay-portlet-ext.tld");
//		baseDeployer.setSecurityTaglibDTD("liferay-security.tld");
//		baseDeployer.setThemeTaglibDTD("liferay-theme.tld");
//		baseDeployer.setUiTaglibDTD("liferay-ui.tld");
//		baseDeployer.setUtilTaglibDTD("liferay-util.tld");
//	}
//
//	private void setJars(final WebDeployer baseDeployer) throws Exception {
//		List<String> jars = new ArrayList<>();
//
//		baseDeployer.addExtJar(jars, "ext-util-bridges.jar");
//		baseDeployer.addExtJar(jars, "ext-util-java.jar");
//		baseDeployer.addExtJar(jars, "ext-util-taglib.jar");
//		baseDeployer.addRequiredJar(jars, "util-bridges.jar");
//		baseDeployer.addRequiredJar(jars, "util-java.jar");
//		baseDeployer.addRequiredJar(jars, "util-taglib.jar");
//		baseDeployer.setJars(jars);
//	}
//}
