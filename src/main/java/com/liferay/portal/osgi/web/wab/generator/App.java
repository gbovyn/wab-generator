package com.liferay.portal.osgi.web.wab.generator;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.liferay.portal.tools.ToolDependencies;
import org.apache.commons.io.FileUtils;

import aQute.bnd.osgi.Constants;

public class App {

	private static final String WEB_CONTEXT_PATH = "Web-ContextPath";

	public static void main(String[] args) throws IOException {
		FileUtils.deleteDirectory(new File("deploy"));
		FileUtils.deleteDirectory(new File("deploys"));
		
		LocalDateTime start = LocalDateTime.now();

		ToolDependencies.wire();

		String filePath = "E:\\Git\\wab-generator-git\\com.liferay.hello.user.jsf.portlet-1.0-SNAPSHOT.war";

		
		File file = new File(filePath);
		//System.out.println(file.exists());

		String[] bundleSymbolicName = { "com.liferay.hello.user.jsf.portlet-1.0-SNAPSHOT" };
		String[] webContextPath = { "/com.liferay.hello.user.jsf.portlet-1.0-SNAPSHOT" };

		Map<String, String[]> parameters = new HashMap<>();
		parameters.put(Constants.BUNDLE_SYMBOLICNAME, bundleSymbolicName);
		parameters.put(WEB_CONTEXT_PATH, webContextPath);
		
		WabProcessorTask wabProcessor = new WabProcessorTask(App.class.getClassLoader(), file, parameters);
		File wab = wabProcessor.getProcessedFile();
		System.out.println(wab);
		
		LocalDateTime end = LocalDateTime.now();
		System.out.println(Duration.between(start, end).getSeconds());
		System.out.println(Duration.between(start, end).toMinutes());
	}
}
