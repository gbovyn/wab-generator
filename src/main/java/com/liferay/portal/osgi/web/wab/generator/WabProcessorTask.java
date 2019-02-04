package com.liferay.portal.osgi.web.wab.generator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.liferay.portal.kernel.deploy.auto.context.AutoDeploymentContext;
import com.liferay.portal.kernel.plugin.PluginPackage;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.ServerDetector;
import com.liferay.portal.kernel.util.SystemProperties;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.osgi.web.wab.generator.internal.processor.WabProcessor;
import com.liferay.portal.tools.deploy.WebDeployer;
import com.liferay.util.ant.ExpandTask;

public class WabProcessorTask extends WabProcessor {

	public WabProcessorTask(ClassLoader classLoader, File file, Map<String, String[]> parameters) {
		super(file, parameters);
	}

	@Override
	protected File autoDeploy() {
		String webContextpath = getWebContextPath();

		AutoDeploymentContext autoDeploymentContext = buildAutoDeploymentContext(webContextpath);
		
		try {
			WebDeployer baseDeployer = setupBaseDeployer();

			//baseDeployer.deployFile(autoDeploymentContext);
			{
				File srcFile = autoDeploymentContext.getFile();
				PluginPackage pluginPackage = autoDeploymentContext.getPluginPackage();

				if (pluginPackage == null) {
					pluginPackage = baseDeployer.readPluginPackage(srcFile);

					autoDeploymentContext.setPluginPackage(pluginPackage);
				}
				
				String specifiedContext = autoDeploymentContext.getContext();

				String displayName = specifiedContext;
				
				//more stuff here for displayname
				
				String deployFilename = null;

				if (Validator.isNotNull(displayName)) {
					deployFilename = displayName + ".war";
				}
				else {
					deployFilename = srcFile.getName();
					displayName = baseDeployer.getDisplayName(srcFile);
				}
				
				Path tempDirPath = Files.createTempDirectory(Paths.get(SystemProperties.get(SystemProperties.TMP_DIR)), null);

				File destinationDir = tempDirPath.toFile();
				
				ExpandTask.expand(srcFile, destinationDir);
				
				File mergeDirFile = new File("/merge/" + srcFile.getName());

				String destDir = autoDeploymentContext.getDestDir();
				File deployDirFile = new File(destDir + "/" + deployFilename);
				
				baseDeployer.deployDirectory(destinationDir, mergeDirFile, deployDirFile, displayName, false, pluginPackage);

				System.out.println("CHECK: "+destinationDir);
				
				File deployDir = new File("deploys");
				//DeleteTask.deleteDirectory(deployDir1);
				deployDir.mkdirs();
				FileUtil.move(destinationDir, deployDir);
				//DeleteTask.deleteDirectory(tempDir);
			}
				
			baseDeployer.close();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		/*
		if (_file.isDirectory()) {
			return _file;
		}
		
		File deployDir = new File("deploys");
		deployDir.mkdirs();
		try (Jar jar = new Jar(_file)) {
			jar.expand(deployDir);
		}
		catch (Exception e) {
			ReflectionUtil.throwException(e);
		}
		*/
		File deployDir = new File("deploys");
		return deployDir;
	}

	private WebDeployer setupBaseDeployer() throws Exception {
		WebDeployer baseDeployer = new WebDeployer();

		baseDeployer.setAppServerType(ServerDetector.TOMCAT_ID);
		baseDeployer.setBaseDir(PropsUtil.get(PropsKeys.AUTO_DEPLOY_DEPLOY_DIR));

		setTlds(baseDeployer);
		setJars(baseDeployer);

		return baseDeployer;
	}

	private void setTlds(final WebDeployer baseDeployer) {
		String PREFIX = "C:\\liferay-ce-portal-7.0-ga7\\tomcat-8.0.32\\webapps\\ROOT\\WEB-INF\\tld\\";

		baseDeployer.setAuiTaglibDTD(PREFIX + "liferay-aui.tld");
		baseDeployer.setPortletTaglibDTD("liferay-portlet_2_0.tld");
		baseDeployer.setPortletExtTaglibDTD("liferay-portlet-ext.tld");
		baseDeployer.setSecurityTaglibDTD("liferay-security.tld");
		baseDeployer.setThemeTaglibDTD("liferay-theme.tld");
		baseDeployer.setUiTaglibDTD("liferay-ui.tld");
		baseDeployer.setUtilTaglibDTD("liferay-util.tld");
	}

	private void setJars(final WebDeployer baseDeployer) throws Exception {
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
