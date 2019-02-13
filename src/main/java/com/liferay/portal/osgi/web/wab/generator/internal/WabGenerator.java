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

package com.liferay.portal.osgi.web.wab.generator.internal;

import com.liferay.portal.osgi.web.wab.generator.internal.processor.WabProcessor;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author Miguel Pastor
 * @author Raymond Augé
 */

public class WabGenerator implements com.liferay.portal.osgi.web.wab.generator.WabGenerator {

    @Override
    public File generate(ClassLoader classLoader, File file, Map<String, String[]> parameters) throws IOException {

        WabProcessor wabProcessor = new WabProcessor(file, parameters);

        return wabProcessor.getProcessedFile();
    }
}