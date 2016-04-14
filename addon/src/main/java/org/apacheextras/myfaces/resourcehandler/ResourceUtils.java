/*
 * Copyright 2011-2012, Jakob Korherr
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apacheextras.myfaces.resourcehandler;

import javax.faces.FacesException;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods.
 *
 * @author Jakob Korherr
 */
public class ResourceUtils
{

    /**
     * The logger for this class.
     */
    private static final Logger log = Logger.getLogger(ResourceUtils.class.getName());

    private static final String HTTP_RESPONSE_DATE_HEADER = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private static final String[] HTTP_REQUEST_DATE_HEADER =
    {
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEEEEE, dd-MMM-yy HH:mm:ss zzz",
        "EEE MMMM d HH:mm:ss yyyy"
    };

    private static TimeZone __GMT = TimeZone.getTimeZone("GMT");

    /**
     * The key with which the the FacesServlet mapping prefix is cached in the application map.
     */
    private static final String FACES_SERVLET_PREFIX_KEY = "org.apacheextras.myfaces.resourcehandler.FACES_SERVLET_PREFIX";

    /**
     * Key for ServletContext attribute containing the tmp directory of the current ServletContext.
     * Specified by Servlet API 2.2 and higher.
     */
    private static final String SERVLETCONTEXT_TMP_DIR_ATTR = "javax.servlet.context.tempdir";

    /**
     * Gets the FacesServlet mapping prefix to use for the request path.
     *
     * @param facesContext
     * @return
     */
    public static String getFacesServletPrefix(FacesContext facesContext)
    {
        ExternalContext externalContext = facesContext.getExternalContext();
        Map<String, Object> applicationMap = externalContext.getApplicationMap();

        // check if already cached
        String prefix = (String) applicationMap.get(FACES_SERVLET_PREFIX_KEY);
        if (prefix == null)
        {
            // try config param in web.xml
            prefix = getFacesServletPrefixFromConfigParam(facesContext);
            if (prefix == null)
            {
                // try to extract it from current request
                prefix = getFacesServletPrefixMappingFromRequest(facesContext);
                if (prefix == null)
                {
                    // none found, use default
                    prefix = RelativeResourceHandler.DEFAULT_FACES_SERVLET_PREFIX;
                    log.warning("Using default FacesServlet prefix \"" + prefix + "\", because no config parameter " +
                            "was found and it also could not be extracted from the current request.");
                }
                else
                {
                    log.info("Using FacesServlet prefix \"" + prefix + "\"");
                }
            }

            // cache it
            applicationMap.put(FACES_SERVLET_PREFIX_KEY, prefix);
        }

        return prefix;
    }

    /**
     * Get the current Thread's ContextClassLoader.
     *
     * Copied from MyFaces' ClassLoaderUtils.
     *
     * @return
     */
    public static ClassLoader getContextClassLoader()
    {
        if (System.getSecurityManager() != null)
        {
            try
            {
                ClassLoader cl = AccessController.doPrivileged(new PrivilegedExceptionAction<ClassLoader>()
                {
                    public ClassLoader run() throws PrivilegedActionException
                    {
                        return Thread.currentThread().getContextClassLoader();
                    }
                });
                return cl;
            }
            catch (PrivilegedActionException pae)
            {
                throw new FacesException(pae);
            }
        }
        else
        {
            return Thread.currentThread().getContextClassLoader();
        }
    }

    /**
     * Get max expire time for resources.
     *
     * @param facesContext
     * @return
     */
    public static long getMaxTimeExpires(FacesContext facesContext)
    {
        ExternalContext externalContext = facesContext.getExternalContext();
        Map<String, Object> applicationMap = externalContext.getApplicationMap();

        // check if already cached
        Long expires = (Long) applicationMap.get(RelativeResourceHandler.RESOURCE_MAX_TIME_EXPIRES_PARAM);
        if (expires == null)
        {
            // get from web.xml
            String webXmlValue = externalContext.getInitParameter(
                    RelativeResourceHandler.RESOURCE_MAX_TIME_EXPIRES_PARAM);
            if (webXmlValue != null)
            {
                try
                {
                    expires = Long.parseLong(webXmlValue);
                }
                catch (NumberFormatException e)
                {
                    throw new FacesException("Config parameter "
                            + RelativeResourceHandler.RESOURCE_MAX_TIME_EXPIRES_PARAM + " must be a Long", e);
                }
            }
            else
            {
                // none found, use default
                expires = RelativeResourceHandler.RESOURCE_MAX_TIME_EXPIRES_DEFAULT;
            }

            // cache it
            applicationMap.put(RelativeResourceHandler.RESOURCE_MAX_TIME_EXPIRES_PARAM, expires);
        }

        return expires;
    }

    /**
     * Returns the String representation of the current locale for the use in the request path.
     *
     * @return
     */
    public static String getRequestLocalePrefix()
    {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        UIViewRoot viewRoot = facesContext.getViewRoot();
        Locale locale = null;
        if (viewRoot == null)
        {
            // use locale prefix from resource request, if possible
            String requestedLocalePrefix = (String) facesContext.getAttributes()
                    .get(RelativeResourceHandler.REQUESTED_LOCALE_PREFIX_CACHE);

            if (requestedLocalePrefix != null && requestedLocalePrefix.length() > 0)
            {
                return requestedLocalePrefix;
            }
        }
        else
        {
            locale = viewRoot.getLocale();
        }

        if (locale == null)
        {
            // fallback to default locale
            locale = Locale.getDefault();
        }
        
        String language = locale.getLanguage();
        String country = locale.getCountry();
        
        if (country != null && country.length() > 0)
        {
            // de_AT
            return language + "_" + country;
        }
        else
        {
            // de
            return language;
        }
    }

    /**
     * Returns the prefix mapping of the FacesServlet from the current request (e.g. /faces).
     * If no mapping can be determined or the current request used extension mapping (e.g. *.jsf)
     * null will be returned.
     *
     * This method was adopted from MyFaces' BaseResourceHandlerSupport.
     *
     * @param facesContext
     * @return
     */
    public static String getFacesServletPrefixMappingFromRequest(FacesContext facesContext)
    {
        ExternalContext externalContext = facesContext.getExternalContext();

        String pathInfo = externalContext.getRequestPathInfo();
        String servletPath = externalContext.getRequestServletPath();

        if (pathInfo != null)
        {
            // If there is a "extra path", it's definitely no extension mapping.
            // Now we just have to determine the path which has been specified
            // in the url-pattern, but that's easy as it's the same as the
            // current servletPath. It doesn't even matter if "/*" has been used
            // as in this case the servletPath is just an empty string according
            // to the Servlet Specification (SRV 4.4).
            return servletPath;
        }
        else
        {
            // In the case of extension mapping, no "extra path" is available.
            // Still it's possible that prefix-based mapping has been used.
            // Actually, if there was an exact match no "extra path"
            // is available (e.g. if the url-pattern is "/faces/*"
            // and the request-uri is "/context/faces").
            int slashPos = servletPath.lastIndexOf('/');
            int extensionPos = servletPath.lastIndexOf('.');
            if (extensionPos > -1 && extensionPos > slashPos)
            {
                // we are only interested in the prefix mapping
                return null;
            }
            else
            {
                // There is no extension in the given servletPath and therefore
                // we assume that it's an exact match using prefix-based mapping.
                return servletPath;
            }
        }
    }

    /**
     * Checks the config parameter defined in {@link RelativeResourceHandler#FACES_SERVLET_PREFIX_PARAM}
     * from web.xml to get a valid faces servlet prefix.
     *
     * @param facesContext
     * @return
     */
    public static String getFacesServletPrefixFromConfigParam(FacesContext facesContext)
    {
        String prefix = facesContext.getExternalContext()
                .getInitParameter(RelativeResourceHandler.FACES_SERVLET_PREFIX_PARAM);

        if (prefix != null && prefix.trim().length() == 0)
        {
            // not null, but empty string
            prefix = null;
        }

        return prefix;
    }

    /**
     * Checks the config parameter defined in {@link RelativeResourceHandler#MAX_CACHE_SIZE_PARAM}
     * from web.xml to get the max cache size to use.
     *
     * @param facesContext
     * @return
     */
    public static int getRelativeResourceMaxCacheSize(FacesContext facesContext)
    {
        String maxCacheParam = facesContext.getExternalContext()
                .getInitParameter(RelativeResourceHandler.MAX_CACHE_SIZE_PARAM);
        if (maxCacheParam != null && maxCacheParam.trim().length() > 0)
        {
            try
            {
                return Integer.parseInt(maxCacheParam);
            }
            catch (NumberFormatException e)
            {
                log.log(Level.SEVERE, "Could not parse config parameter " +
                        RelativeResourceHandler.MAX_CACHE_SIZE_PARAM +
                        ", will use default value (" +
                        RelativeResourceHandler.DEFAULT_MAX_CACHE_SIZE + ") instead.", e);
            }
        }

        // use default value
        return RelativeResourceHandler.DEFAULT_MAX_CACHE_SIZE;
    }

    /**
     * Taken from MyFaces' ResourceLoaderUtils.
     *
     * @param url
     * @return
     * @throws IOException
     */
    public static long getResourceLastModified(URL url) throws IOException
    {
        if ("file".equals(url.getProtocol()))
        {
            String externalForm = url.toExternalForm();
            // Remove the "file:"
            File file = new File(externalForm.substring(5));

            return file.lastModified();
        }
        else
        {
            return getResourceLastModified(url.openConnection());
        }
    }

    /**
     * Taken from MyFaces' ResourceLoaderUtils.
     * 
     * @param connection
     * @return
     * @throws IOException
     */
    public static long getResourceLastModified(URLConnection connection) throws IOException
    {
        long modified;
        if (connection instanceof JarURLConnection)
        {
            // The following hack is required to work-around a JDK bug.
            // getLastModified() on a JAR entry URL delegates to the actual JAR file
            // rather than the JAR entry.
            // This opens internally, and does not close, an input stream to the JAR
            // file.
            // In turn, you cannot close it by yourself, because it's internal.
            // The work-around is to get the modification date of the JAR file
            // manually,
            // and then close that connection again.

            URL jarFileUrl = ((JarURLConnection) connection).getJarFileURL();
            URLConnection jarFileConnection = jarFileUrl.openConnection();

            try
            {
                modified = jarFileConnection.getLastModified();
            }
            finally
            {
                try
                {
                    jarFileConnection.getInputStream().close();
                }
                catch (Exception exception)
                {
                    // Ignored
                }
            }
        }
        else
        {
            modified = connection.getLastModified();
        }

        return modified;
    }

    /**
     * Taken from MyFaces' ResourceLoaderUtils. 
     *
     * @param value
     * @return
     */
    public static String formatDateHeader(long value)
    {
        SimpleDateFormat format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER, Locale.US);
        format.setTimeZone(__GMT);
        return format.format(new Date(value));
    }

    /**
     * Taken from MyFaces' ResourceLoaderUtils.
     *
     * @param value
     * @return
     */
    public static Long parseDateHeader(String value)
    {
        Date date = null;
        for (int i = 0; (date == null) && (i < HTTP_REQUEST_DATE_HEADER.length); i++)
        {
            try
            {
                SimpleDateFormat format = new SimpleDateFormat(HTTP_REQUEST_DATE_HEADER[i], Locale.US);
                format.setTimeZone(__GMT);
                date = format.parse(value);
            }
            catch (ParseException e)
            {
                // nothing
            }
        }
        if (date == null)
        {
            return null;
        }
        return date.getTime();
    }

    /**
     * Returns the tmp dir of the current ServletContext,
     * which every Servlet container has to provide.
     *
     * @param facesContext
     * @return
     */
    public static File getServletContextTmpDir(FacesContext facesContext)
    {
        // note that the ApplicationMap directly accesses the ServletContext attribute map
        return (File) facesContext.getExternalContext().getApplicationMap().get(SERVLETCONTEXT_TMP_DIR_ATTR);
    }

    /**
     * Reads the specified input stream into the provided byte array storage and
     * writes it to the output stream.
     *
     * @param in
     * @param out
     * @param buffer
     * @throws IOException
     */
    public static void pipeBytes(InputStream in, OutputStream out, byte[] buffer) throws IOException
    {
        int length;

        while ((length = (in.read(buffer))) >= 0)
        {
            out.write(buffer, 0, length);
        }
    }

    /**
     * Removes leading and trailing slashes (= '/') from the given String.
     *
     * @param s
     * @return
     */
    public static String trimSlashes(String s)
    {
        if (s != null)
        {
            // remove at begin
            int index = 0;
            int startIndex = index;
            while (s.charAt(index) == '/')
            {
                index++;
            }
            if (index != startIndex)
            {
                s = s.substring(index);
            }

            // remove at end
            index = s.length() - 1;
            startIndex = index;
            while (s.charAt(index) == '/')
            {
                index--;
            }
            if (index != startIndex)
            {
                s = s.substring(0, index + 1);
            }
        }

        return s;
    }

    /**
     * Checks if the user agent supports GZIP compression on basis of the "Accept-Encoding" header field. 
     * Created according to RFC2616, section 14.3 Accept-Encoding.
     *
     * Some examples of Accept-Encoding:
     *
     *     Accept-Encoding: gzip, deflate
     *     Accept-Encoding:
     *     Accept-Encoding: *
     *     Accept-Encoding: compress;q=0.5, gzip;q=1.0
     *     Accept-Encoding: gzip;q=1.0, identity; q=0.5, *;q=0 
     *
     * @param acceptEncodingHeader
     * @return
     */
    public static boolean isGZIPEncodingAccepted(String acceptEncodingHeader)
    {
        if (acceptEncodingHeader == null)
        {
            // no accept-encoding header set, must assume gzip is not supported
            return false;
        }

        int gzipIndex = acceptEncodingHeader.indexOf("gzip");
        if (gzipIndex != -1)
        {
            // "gzip" appears in the header
            // --> check if banned via q=0
            return !isEncodingQValueZero(acceptEncodingHeader, gzipIndex);
        }

        // no "gzip" in header --> check for "*"
        int starIndex = acceptEncodingHeader.indexOf('*');
        if (starIndex != -1)
        {
            // "*" appears in the header
            // --> check if banned via q=0
            return !isEncodingQValueZero(acceptEncodingHeader, starIndex);
        }

        // neither "gzip" nor "*" in header
        return false;
    }

    private static boolean isEncodingQValueZero(String acceptEncodingHeader, int startIndex)
    {
        // remove any precending definitions
        String encodingSubstring = acceptEncodingHeader.substring(startIndex);

        // remove any subsequent definitions (separated via ,)
        int commaIndex = encodingSubstring.indexOf(',');
        if (commaIndex != -1)
        {
            encodingSubstring = encodingSubstring.substring(0, commaIndex);
        }

        int qZeroIndex = encodingSubstring.indexOf("q=0");
        if (qZeroIndex != -1)
        {
            String qZeroSubstring = encodingSubstring.substring(qZeroIndex).trim();
            if (qZeroSubstring.matches("q=0(\\.(0){0,3})?"))
            {
                // "q=0" or "q=0." or "q=0.0" or "q=0.00" or "q=0.000" found
                // NOTE that there MUST NOT be more than 3 digits after the decimal point (per RFC section 3.9)
                return true;
            }
            else
            {
                // q=0.xyz" found with any of xyz being != 0
                return false;
            }
        }
        else
        {
            // "q=0" not found
            return false;
        }
    }
    
}
