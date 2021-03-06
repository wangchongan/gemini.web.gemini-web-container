/*******************************************************************************
 * Copyright (c) 2010 SAP AG
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution. 
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Apache License v2.0 is available at 
 *   http://www.opensource.org/licenses/apache2.0.php.
 * You may elect to redistribute this code under either of these licenses.  
 *
 * Contributors:
 *   Violeta Georgieva - initial contribution
 *******************************************************************************/

package org.eclipse.gemini.web.tomcat.internal;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.eclipse.gemini.web.core.spi.ServletContainerException;
import org.osgi.framework.Bundle;

public class WebappConfigLocator {

    static final String DEFAULT_CONFIG_DIRECTORY = "config";

    private static final String DEFAULT_CONTEXT_XML = "context.xml";

    private static final String DEFAULT_WEB_XML = "web.xml";

    static final String CONTEXT_XML = "META-INF/context.xml";

    private static final String XML_EXTENSION = ".xml";

    private static final String ROOT_PATH = "/";

    private static final String ROOT_CONTEXT_FILE = "ROOT";

    private static final char SLASH_SEPARATOR = '/';

    private static final char HASH_SEPARATOR = '#';

    static final String JAR_SCHEMA = "jar:";

    static final String JAR_TO_ENTRY_SEPARATOR = "!/";

    static final String EMPTY_STRING = "";

    /**
     * Resolves the default context.xml and returns a relative path to it, if it exists in the main Tomcat's
     * configuration directory, otherwise returns <code>null</code>. The method returns <code>null</code> also in case
     * main Tomcat's configuration directory does not exists.
     * 
     * @param configLocation the main Tomcat's configuration directory
     * @return a relative path to default context.xml file, if it exists in the main Tomcat's configuration directory,
     *         otherwise returns <code>null</code>.
     */
    public static String resolveDefaultContextXml(File configLocation) {
        if (configLocation == null) {
            return null;
        }

        File defaultContextXml = new File(configLocation, DEFAULT_CONTEXT_XML);
        if (defaultContextXml.exists()) {
            return defaultContextXml.getAbsolutePath();
        } else {
            return null;
        }
    }

    /**
     * Resolves the default web.xml and returns an absolute path to it, if it exists in the main Tomcat's configuration
     * directory, otherwise returns <code>null</code>. The method returns <code>null</code> also in case main Tomcat's
     * configuration directory does not exists. Note: If a default web.xml is not found then web-embed.xml will be used
     * as default web.xml. (web-embed.xml is provided by this bundle)
     * 
     * @param configLocation the main Tomcat's configuration directory
     * @return an absolute path to default web.xml file, if it exists in the main Tomcat's configuration directory,
     *         otherwise returns <code>null</code>.
     */
    public static String resolveDefaultWebXml(File configLocation) {
        if (configLocation == null) {
            return null;
        }

        File defaultWebXml = new File(configLocation, DEFAULT_WEB_XML);
        if (defaultWebXml.exists()) {
            return defaultWebXml.getAbsolutePath();
        }

        return null;
    }

    /**
     * Resolves the web application's specific context.xml The algorithm is the following:
     * <ol>
     * <li>The composite context path i.e. /foo/bar are formated. The "/" are replaced with "#" i.e. foo#bar</li>
     * <li>Check for <code><formated-web-app-context-path>.xml</code> file in the main Tomcat Host's configuration
     * directory, use if found</li>
     * <li>If <code>docBase</code> is directory check for <code>META-INF/context.xml</code> file, use if found</li>
     * <li>If <code>docBase</code> is an archive check for <code>META-INF/context.xml</code> entry. If it exists, copy
     * it to the main Tomcat Host's configuration directory and use it. The next time this copy will be used instead of
     * the one in the archive.</li>
     * <li>Return <code>null</code> in other cases.</li>
     * </ol>
     * 
     * @param path the context path
     * @param docBase the root directory/file for the web application
     * @param configLocation Host's configuration directory
     * @param bundle the corresponding web application bundle
     * @return the context.xml if it is found following the algorithm above, otherwise <code>null</code>
     * @throws MalformedURLException
     */
    public static URL resolveWebappContextXml(String path, String docBase, File configLocation, Bundle bundle) throws MalformedURLException {
        path = formatContextPath(path);

        // Try to find the context.xml in the Tomcat's configuration directory
        File contextXml = new File(configLocation, path + XML_EXTENSION);
        if (contextXml.exists()) {
            return contextXml.toURI().toURL();
        }

        // Try to find the context.xml in docBase
        if (EMPTY_STRING.equals(docBase)) {
            if (bundle != null) {
                return resolveWebappContextXmlFromJarURLConnection(bundle);
            } else {
                return null;
            }
        }

        File docBaseFile = new File(docBase);
        if (docBaseFile.isDirectory()) {
            contextXml = new File(docBaseFile, CONTEXT_XML);
            if (contextXml.exists()) {
                return contextXml.toURI().toURL();
            }
        } else {
            JarFile jar = null;
            try {
                jar = new JarFile(docBaseFile);
                ZipEntry contextXmlEntry = jar.getEntry(CONTEXT_XML);
                if (contextXmlEntry != null) {
                    return new URL(JAR_SCHEMA + docBaseFile.toURI().toString() + JAR_TO_ENTRY_SEPARATOR + CONTEXT_XML);
                }
            } catch (IOException e) {
                throw new ServletContainerException("Cannot open for reading " + docBaseFile.getAbsolutePath(), e);
            } finally {
                if (jar != null) {
                    try {
                        jar.close();
                    } catch (IOException _) {
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resolves the directory where the web application' context.xml files are placed. Typically this is the Host's
     * configuration directory.
     * 
     * @param mainConfigDir the main Tomcat's configuration directory
     * @param host host
     * @return the directory where the web applications' context.xml files are placed.
     */
    public static File resolveWebappConfigDir(File mainConfigDir, Host host) {
        mainConfigDir = mainConfigDir != null ? mainConfigDir : new File(DEFAULT_CONFIG_DIRECTORY);

        File configLocation = mainConfigDir;

        Container parent = host.getParent();
        if (parent != null && parent instanceof Engine) {
            configLocation = new File(configLocation, parent.getName());
        }

        return new File(configLocation, host.getName());
    }

    private static String formatContextPath(String contextPath) {
        // Multi-level context paths may be defined using #, e.g. foo#bar.xml for a context path of /foo/bar.
        if (contextPath.equals(ROOT_PATH)) {
            contextPath = ROOT_CONTEXT_FILE;
        } else if (SLASH_SEPARATOR == contextPath.charAt(0)) {
            contextPath = contextPath.substring(1);
        }
        return contextPath.replace(SLASH_SEPARATOR, HASH_SEPARATOR);
    }

    private static URL resolveWebappContextXmlFromJarURLConnection(Bundle bundle) {
        URL bundleUrl;
        try {
            bundleUrl = new URL(JAR_SCHEMA + bundle.getLocation() + JAR_TO_ENTRY_SEPARATOR + CONTEXT_XML);
        } catch (MalformedURLException e) {
            return null;
        }

        JarFile jarFile = null;
        try {
            URLConnection connection = bundleUrl.openConnection();

            if (connection instanceof JarURLConnection) {
                JarURLConnection jarURLConnection = (JarURLConnection) connection;
                jarURLConnection.setUseCaches(false);
                jarFile = jarURLConnection.getJarFile();
                String entryName = jarURLConnection.getEntryName();
                if (entryName != null && jarFile != null && jarFile.getEntry(entryName) != null) {
                    return bundleUrl;
                }
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException _) {
                }
            }
        }

        return null;
    }
}
