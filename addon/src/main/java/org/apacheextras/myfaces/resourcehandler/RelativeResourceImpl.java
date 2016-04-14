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

import org.apacheextras.myfaces.resourcehandler.config.Library;
import org.apacheextras.myfaces.resourcehandler.el.ValueExpressionEvaluationInputStream;
import org.apacheextras.myfaces.resourcehandler.provider.ClassPathResourceProvider;
import org.apacheextras.myfaces.resourcehandler.provider.ExternalResourceProvider;
import org.apacheextras.myfaces.resourcehandler.provider.ResourceProvider;
import org.apacheextras.myfaces.resourcehandler.provider.ResourceProviderChain;
import org.apacheextras.myfaces.resourcehandler.provider.WebappResourceProvider;

import javax.faces.FacesException;
import javax.faces.application.ProjectStage;
import javax.faces.application.ResourceHandler;
import javax.faces.context.FacesContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Default RelativeResource implementation.
 *
 * @author Jakob Korherr
 */
public class RelativeResourceImpl extends RelativeResource
{

    private static final Logger logger = Logger.getLogger(RelativeResourceImpl.class.getName());

    /**
     * Default ResourceProvider to use for loading resource files on the server.
     */
    protected static final ResourceProvider DEFAULT_RESOURCE_PROVIDER = new ResourceProviderChain(Arrays.asList(
            new ClassPathResourceProvider(ClassPathResourceProvider.CLASSPATH_META_INF_RESOURCES, true),
            new WebappResourceProvider(WebappResourceProvider.WEBAPP_META_INF_RESOURCES, true),
            new WebappResourceProvider(WebappResourceProvider.WEBAPP_RESOURCES, true)
    ));

    /**
     * Subdir of the ServletContext tmp dir to store compressed resources.
     */
    protected static final String CACHE_BASE_DIR = "relative-resource-handler-cache/";

    /**
     * Suffix for compressed files.
     */
    protected static final String COMPRESSED_FILE_SUFFIX = ".gzip";

    /**
     * Suffix for el evaluated files.
     */
    protected static final String EL_EVALUATED_FILE_SUFFIX = ".evaluated";

    /**
     * Size of the byte array buffer.
     */
    protected static final int BUFFER_SIZE = 2048;

    /**
     * Accept-Encoding HTTP header field.
     */
    protected static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";

    private Library library;
    private String requestedLocalePrefix;
    private final boolean gzipEnabled;
    private final boolean localeSupportEnabled;
    private final String urlVersion;
    private final boolean developmentStage;
    private Boolean evaluateElExpressions;

    private boolean initialized = false;
    private URL cachedUrl;

    public RelativeResourceImpl(String resourceName, Library library, String contentType,
                            String requestedLocalePrefix, boolean gzipEnabled,
                            boolean localeSupportEnabled, String urlVersion)
    {
        this.setResourceName(resourceName);
        this.setLibraryName(library.getName());
        this.library = library;
        this.requestedLocalePrefix = requestedLocalePrefix;
        this.gzipEnabled = gzipEnabled;
        this.localeSupportEnabled = localeSupportEnabled;
        this.urlVersion = urlVersion;

        FacesContext facesContext = FacesContext.getCurrentInstance();
        developmentStage = facesContext.isProjectStage(ProjectStage.Development);

        // handle contentType
        if (contentType == null)
        {
            //Resolve contentType using ExternalContext.getMimeType
            contentType = facesContext.getExternalContext().getMimeType(resourceName);
        }
        this.setContentType(contentType);
    }

    @Override
    public void setLibraryName(String libraryName)
    {
        // trim slashes
        super.setLibraryName(ResourceUtils.trimSlashes(libraryName));
    }

    @Override
    public void setResourceName(String resourceName)
    {
        // trim slashes
        super.setResourceName(ResourceUtils.trimSlashes(resourceName));
    }

    /**
     * Initialize the RelativeResource.
     * This is separated from the constructor in order to perform valid initialization in wrapped relative resources.
     */
    @Override
    public synchronized void initialize(FacesContext facesContext)
    {
        if (initialized)
        {
            return;   // already initialized
        }

        // handle localePrefix
        if (localeSupportEnabled)
        {
            if (requestedLocalePrefix == null)
            {
                // use Locale from ViewRoot or current resource request if non set
                requestedLocalePrefix = ResourceUtils.getRequestLocalePrefix();
            }

            // check if resource exists with locale prefix in path
            if (!resourceExists())
            {
                // check for language only (if not already done)
                int underscoreIndex = requestedLocalePrefix.indexOf('_');
                if (underscoreIndex != -1)
                {
                    // only use "de" instead of "de_AT"
                    requestedLocalePrefix = requestedLocalePrefix.substring(0, underscoreIndex);

                    // check if resource exists with locale prefix in path
                    if (!resourceExists())
                    {
                        // do not use the locale prefix
                        // NOTE that for the request path ResourceUtils.getRequestLocalePrefix() will be used
                        requestedLocalePrefix = null;
                    }
                }
                else
                {
                    // do not use the locale prefix
                    // NOTE that for the request path ResourceUtils.getRequestLocalePrefix() will be used
                    requestedLocalePrefix = null;
                }
            }
        }
        else
        {
            requestedLocalePrefix = null;
        }

        // handle el-evaluation (must also work in ProjectStage = Development)
        // NOTE that this must happen before compressing the resource!
        if (shouldEvaluateElExpressions() && !isElEvaluatedVersionAvailable(facesContext))
        {
            try
            {
                // pre-evaluate expressions and cache evaluated resource
                // (NOTE that el expressions must be application scoped in order to be correct)
                createElEvaluatedVersion(facesContext);
            }
            catch (IOException ioe)
            {
                // we were not able to create the el evaluated version
                logger.log(Level.WARNING, "Could not create el-evaluated version of Resource " + this, ioe);
            }
        }

        // handle compression (only available if ProjectStage != Development).
        // NOTE that this must happen after EL evaluation!
        if (gzipEnabled && !developmentStage && isCompressible() && !isCompressedVersionAvailable(facesContext))
        {
            try
            {
                // create compressed version
                createCompressedVersion(facesContext);
            }
            catch (IOException ioe)
            {
                // we were not able to create the compressed version
                logger.log(Level.WARNING, "Could not create compressed version of Resource " + this, ioe);
            }
        }

        initialized = true; // finally, set initialized to true to avoid double initialization
    }

    @Override
    public boolean isInitialized()
    {
        return initialized;
    }

    /**
     * Returns true if this resource really exists.
     *
     * @return
     */
    @Override
    public boolean resourceExists()
    {
        // an external resource does always exits
        // local resources only if getURL() returns a non-null value
        return (library.getLocationType() == Library.LocationType.EXTERNAL) || (getURL() != null);
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        FacesContext facesContext = FacesContext.getCurrentInstance();

        if (shouldServeCompressedVersion(facesContext))
        {
            return getCompressedInputStream(facesContext);
        }

        return getUncompressedInputStreamElEvaluationAware(facesContext);
    }

    @Override
    public URL getURL()
    {
        if (cachedUrl != null)
        {
            return cachedUrl;
        }

        // delegate to resource provider
        cachedUrl = getResourceProvider().getUrl(FacesContext.getCurrentInstance(), this);

        return cachedUrl;
    }

    @Override
    public String getRequestPath()
    {
        if (library.getLocationType() == Library.LocationType.EXTERNAL)
        {
            // external resources have a static request path
            return library.getLocation() + "/" + getResourceName();
        }
        else
        {
            // local resource
            FacesContext facesContext = FacesContext.getCurrentInstance();

            StringBuilder path = new StringBuilder();
            path.append(ResourceUtils.getFacesServletPrefix(facesContext));
            path.append(ResourceHandler.RESOURCE_IDENTIFIER);
            path.append("/");
            path.append(getRelativePath());

            return facesContext.getApplication().getViewHandler().getResourceURL(facesContext, path.toString());
        }
    }

    @Override
    public String getRelativePath()
    {
        StringBuilder path = new StringBuilder();
        path.append(urlVersion);
        path.append("/");

        // append current locale prefix, if enabled
        if (localeSupportEnabled)
        {
            // calculate current localePrefix (could be different from the one requested, e.g. on locale change)
            path.append(ResourceUtils.getRequestLocalePrefix());
            path.append("/");
        }
        path.append(getLibraryName());
        path.append("/");
        path.append(getResourceName());

        return path.toString();
    }

    @Override
    public String getRequestedLocalePrefix()
    {
        return requestedLocalePrefix;
    }

    @Override
    public Map<String, String> getResponseHeaders()
    {
        // Adopted from MyFaces' ResourceImpl
        FacesContext facesContext = FacesContext.getCurrentInstance();

        if (facesContext.getApplication().getResourceHandler().isResourceRequest(facesContext))
        {
            Map<String, String> headers = new HashMap<String, String>();

            long lastModified;
            try
            {
                lastModified = ResourceUtils.getResourceLastModified(this.getURL());
            }
            catch (IOException e)
            {
                lastModified = -1;
            }

            if (lastModified >= 0)
            {
                headers.put("Last-Modified", ResourceUtils.formatDateHeader(lastModified));

                long expires;
                if (facesContext.isProjectStage(ProjectStage.Development))
                {
                    // Force to expire now to prevent caching on development time.
                    expires = System.currentTimeMillis();
                }
                else
                {
                    expires = System.currentTimeMillis() + ResourceUtils.getMaxTimeExpires(facesContext);
                }
                headers.put("Expires", ResourceUtils.formatDateHeader(expires));
            }

            // add header if we're using content compression
            if (shouldServeCompressedVersion(facesContext))
            {
                headers.put("Content-Encoding", "gzip");
            }

            return headers;
        }
        else
        {
            //No need to return headers
            return Collections.emptyMap();
        }
    }

    @Override
    public boolean userAgentNeedsUpdate(FacesContext facesContext)
    {
        if (developmentStage)
        {
            // always re-send resources in development
            return true;
        }

        // adopted from MyFaces' ResourceImpl:

        // RFC2616 says related to If-Modified-Since header the following:
        //
        // "... The If-Modified-Since request-header field is used with a method to
        // make it conditional: if the requested variant has not been modified since
        // the time specified in this field, an entity will not be returned from
        // the server; instead, a 304 (not modified) response will be returned
        // without any message-body..."
        //
        // This method is called from ResourceHandlerImpl.handleResourceRequest and if
        // returns false send a 304 Not Modified response.

        String ifModifiedSinceString = facesContext.getExternalContext().getRequestHeaderMap().get("If-Modified-Since");
        if (ifModifiedSinceString == null)
        {
            return true;
        }

        Long ifModifiedSince = ResourceUtils.parseDateHeader(ifModifiedSinceString);
        if (ifModifiedSince == null)
        {
            return true;
        }

        Long lastModified;
        try
        {
            lastModified = ResourceUtils.getResourceLastModified(this.getURL());
        }
        catch (IOException exception)
        {
            lastModified = -1L;
        }

        if (lastModified >= 0)
        {
            // If the lastModified date is lower or equal than ifModifiedSince,
            // the agent does not need to update.
            // Note the lastModified time is set at milisecond precision, but when
            // the date is parsed and sent on ifModifiedSince, the exceding miliseconds
            // are trimmed. So, we have to compare trimming this from the calculated
            // lastModified time.
            if ( (lastModified-(lastModified % 1000)) <= ifModifiedSince)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the relative path for the resource file on the server.
     * This has the format [localePrefix/]libraryName/resourceName.
     *
     * @return
     */
    @Override
    public String getResourceFilePath()
    {
        return getResourceFilePath(true);
    }

    @Override
    public String getResourceFilePath(boolean includeLibraryName)
    {
        StringBuilder path = new StringBuilder();
        if (requestedLocalePrefix != null)
        {
            path.append(requestedLocalePrefix);
            path.append("/");
        }
        if (includeLibraryName)
        {
            path.append(getLibraryName());
            path.append("/");
        }
        path.append(getResourceName());

        return path.toString();
    }

    private boolean isCompressible()
    {
        // GZIP compression is supported for local .css and .js files
        return library.getLocationType() != Library.LocationType.EXTERNAL
                && (getResourceName().endsWith(".css") || getResourceName().endsWith(".js"));
    }

    private boolean userAgentSupportsCompression(FacesContext facesContext)
    {
        String acceptEncodingHeader = facesContext.getExternalContext()
                .getRequestHeaderMap().get(ACCEPT_ENCODING_HEADER);

        return ResourceUtils.isGZIPEncodingAccepted(acceptEncodingHeader);
    }

    private boolean shouldServeCompressedVersion(FacesContext facesContext)
    {
        // we should serve a compressed version of the resource, if
        //   - GZIP compression is enabled
        //   - ProjectStage != Development
        //   - a compressed version is available (created in constructor)
        //   - the user agent supports compresssion
        return gzipEnabled
                && !developmentStage
                && isCompressedVersionAvailable(facesContext)
                && userAgentSupportsCompression(facesContext);
    }

    private boolean isCompressedVersionAvailable(FacesContext facesContext)
    {
        return getCompressedFile(facesContext).exists();
    }

    private File getCompressedFile(FacesContext facesContext)
    {
        File tmpDir = ResourceUtils.getServletContextTmpDir(facesContext);

        return new File(tmpDir, CACHE_BASE_DIR + getResourceFilePath() + COMPRESSED_FILE_SUFFIX);
    }

    private boolean isElEvaluatedVersionAvailable(FacesContext facesContext)
    {
        return getElEvaluatedFile(facesContext).exists();
    }

    private File getElEvaluatedFile(FacesContext facesContext)
    {
        File tmpDir = ResourceUtils.getServletContextTmpDir(facesContext);

        return new File(tmpDir, CACHE_BASE_DIR + getResourceFilePath() + EL_EVALUATED_FILE_SUFFIX);
    }

    private boolean shouldEvaluateElExpressions()
    {
        if (evaluateElExpressions == null)
        {
            for (String fileMask : library.getElEvaluationFileMasks())
            {
                if (fileMaskMatches(fileMask))
                {
                    evaluateElExpressions = true;
                    break;
                }
            }
            if (evaluateElExpressions == null)
            {
                evaluateElExpressions = false;
            }
        }

        return evaluateElExpressions;
    }

    private boolean fileMaskMatches(String fileMask)
    {
        fileMask = fileMask.replace("*", "\\E.*\\Q");
        fileMask = "\\Q" + fileMask + "\\E";

        return getResourceName().matches(fileMask);
    }

    private InputStream getCompressedInputStream(FacesContext facesContext) throws IOException
    {
        return new FileInputStream(getCompressedFile(facesContext));
    }

    private InputStream getUncompressedInputStreamElEvaluationAware(FacesContext facesContext) throws IOException
    {
        if (isElEvaluatedVersionAvailable(facesContext))
        {
            return new FileInputStream(getElEvaluatedFile(facesContext));
        }
        else
        {
            // no el-evaluation, return pure input stream
            return getPureInputStream(facesContext);
        }
    }

    private InputStream getPureInputStream(FacesContext facesContext) throws IOException
    {
        // delegate to resource provider
        return getResourceProvider().getInputStream(facesContext, this);
    }

    /**
     * Uses GZIPOutputStream to compress this resource.
     * It will be stored where getCompressedFile() points to.
     *
     * Note that the resource really must be compressible (isCompressible() must return true).
     *
     * @param facesContext
     * @throws IOException
     */
    private void createCompressedVersion(FacesContext facesContext) throws IOException
    {
        File target = getCompressedFile(facesContext);
        target.mkdirs();  // ensure necessary directories exist
        target.delete();  // remove any existing file

        InputStream inputStream = null;
        FileOutputStream fileOutputStream;
        GZIPOutputStream gzipOutputStream = null;
        try
        {
            inputStream = getUncompressedInputStreamElEvaluationAware(facesContext);
            fileOutputStream = new FileOutputStream(target);
            gzipOutputStream = new GZIPOutputStream(fileOutputStream);
            byte[] buffer = new byte[BUFFER_SIZE];

            ResourceUtils.pipeBytes(inputStream, gzipOutputStream, buffer);
        }
        finally
        {
            if (inputStream != null)
            {
                inputStream.close();
            }
            if (gzipOutputStream != null)
            {
                // also closes fileOutputStream
                gzipOutputStream.close();
            }
        }
    }

    /**
     * Uses ValueExpressionEvaluationInputStream to evaluate el expressions this resource.
     * It will be stored where getElEvaluatedFile() points to.
     *
     * @param facesContext
     * @throws IOException
     */
    private void createElEvaluatedVersion(FacesContext facesContext) throws IOException
    {
        File target = getElEvaluatedFile(facesContext);
        target.mkdirs();  // ensure necessary directories exist
        target.delete();  // remove any existing file

        // indicate that we are currently evaluating EL expressions of a resource.
        // we need to know this, b/c ResourceHandler.createResource() can be called while
        // evaluating #{resource['']} expressions, and we need to treat this case differently in the ResourceHandler.
        facesContext.getAttributes().put(RelativeResourceHandler.EVALUATING_RESOURCE_EL_EXPRESSIONS, Boolean.TRUE);

        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        try
        {
            inputStream = new ValueExpressionEvaluationInputStream(facesContext, getPureInputStream(facesContext));
            fileOutputStream = new FileOutputStream(target);
            byte[] buffer = new byte[BUFFER_SIZE];

            ResourceUtils.pipeBytes(inputStream, fileOutputStream, buffer);
        }
        finally
        {
            facesContext.getAttributes().put(RelativeResourceHandler.EVALUATING_RESOURCE_EL_EXPRESSIONS, Boolean.FALSE);
            if (inputStream != null)
            {
                inputStream.close();
            }
            if (fileOutputStream != null)
            {
                fileOutputStream.close();
            }
        }
    }

    private ResourceProvider getResourceProvider()
    {
        ResourceProvider libraryResourceProvider = library.getResourceProvider();
        if (libraryResourceProvider != null)
        {
            return libraryResourceProvider;
        }

        // determine the resource provider to use
        if (library.getLocationType() != null)
        {
            switch (library.getLocationType())
            {
                case CLASSPATH:
                    libraryResourceProvider = new ClassPathResourceProvider(library.getLocation());
                    break;

                case WEBAPP:
                    libraryResourceProvider = new WebappResourceProvider(library.getLocation());
                    break;

                case EXTERNAL:
                    libraryResourceProvider = new ExternalResourceProvider();
                    break;

                // should not happen, but just to be sure we throw an exception here (to avoid a failure scenario)
                default: throw new FacesException("Illegal enum value for " + Library.LocationType.class.getName());
            }
        }
        else
        {
            // no location provided, use default provider
            libraryResourceProvider = DEFAULT_RESOURCE_PROVIDER;
        }

        // set the determined resource provider on the library for future access
        library.setResourceProvider(libraryResourceProvider);

        return libraryResourceProvider;
    }

}
