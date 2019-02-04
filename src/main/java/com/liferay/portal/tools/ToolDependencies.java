/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.tools;

import com.liferay.portal.kernel.security.xml.SecureXMLFactoryProviderUtil;
import com.liferay.portal.kernel.util.*;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.kernel.xml.UnsecureSAXReaderUtil;
import com.liferay.portal.security.xml.SecureXMLFactoryProviderImpl;
import com.liferay.portal.util.*;
import com.liferay.portal.xml.SAXReaderImpl;

/**
 * Original file: https://github.com/liferay/liferay-portal/blob/master/portal-impl/src/com/liferay/portal/tools/ToolDependencies.java
 */
public class ToolDependencies {

    public static void wire() {
        wireProps();
        wireBasic();
        wireDeployers();
    }

    private static void wireBasic() {
        FileUtil fileUtil = new FileUtil();

        fileUtil.setFile(new FileImpl());

        HtmlUtil htmlUtil = new HtmlUtil();

        htmlUtil.setHtml(new HtmlImpl());

        HttpUtil httpUtil = new HttpUtil();

        httpUtil.setHttp(new HttpImpl());

        SAXReaderUtil saxReaderUtil = new SAXReaderUtil();

        SAXReaderImpl secureSAXReader = new SAXReaderImpl();

        secureSAXReader.setSecure(true);

        saxReaderUtil.setSAXReader(secureSAXReader);

        SecureXMLFactoryProviderUtil secureXMLFactoryProviderUtil = new SecureXMLFactoryProviderUtil();

        secureXMLFactoryProviderUtil.setSecureXMLFactoryProvider(new SecureXMLFactoryProviderImpl());

        UnsecureSAXReaderUtil unsecureSAXReaderUtil = new UnsecureSAXReaderUtil();

        SAXReaderImpl unsecureSAXReader = new SAXReaderImpl();

        unsecureSAXReaderUtil.setSAXReader(unsecureSAXReader);
    }

    private static void wireDeployers() {
        PortalUtil portalUtil = new PortalUtil();

        portalUtil.setPortal(new PortalImpl());
    }

    private static void wireProps() {
        PropsUtil.setProps(new PropsImpl());
    }
}
