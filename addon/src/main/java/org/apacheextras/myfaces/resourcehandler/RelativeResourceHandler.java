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

import org.apacheextras.myfaces.resourcehandler.cache.RelativeResourceCache;
import org.apacheextras.myfaces.resourcehandler.config.DefaultRelativeResourceHandlerConfigProvider;
import org.apacheextras.myfaces.resourcehandler.config.RelativeResourceHandlerConfig;
import org.apacheextras.myfaces.resourcehandler.resolver.DefaultRelativeResourceResolver;
import org.apacheextras.myfaces.resourcehandler.resolver.RelativeResourceResolver;
import org.apacheextras.myfaces.resourcehandler.spi.RelativeResourceHandlerConfigProvider;
import org.apacheextras.myfaces.resourcehandler.spi.RelativeResourceResolverProvider;

import javax.faces.FacesException;
import javax.faces.application.Resource;
import javax.faces.application.ResourceHandler;
import javax.faces.application.ResourceHandlerWrapper;
import javax.faces.context.FacesContext;
import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * <p>Custom JSF 2 ResourceHandler implementation, supporting:</p>
 * <ul>
 *   <li>relative paths between resources (css files referencing images without using #resource['..'])</li>
 *   <li>caching resources in the client (disabled if ProjectStage == Development)</li>
 *   <li>GZIP compression and local cache in tmp dir (disabled if ProjectStage == Development)</li>
 *   <li>i18n (supporting country code and language).</li>
 * </ul>
 *
 * The i18n mechanism looks up the resource in the following order (e.g. current Locale is "de_AT"):
 * <ol>
 *   <li>de_AT/libraryName/resourceName</li>
 *   <li>de/libraryName/resourceName</li>
 *   <li>libraryName/resourceName</li>
 * </ol>
 *
 * <p>Libraries handled by this ResourceHandler must be specified in
 * META-INF/relative-resources.xml in the following format:</p>
 * <pre>
 * &lt;relative-resources&gt;
 *     &lt;libraries&gt;
 *         &lt;library&gt;library1&lt;/library&gt;
 *         &lt;library&gt;library2&lt;/library&gt;
 *     &lt;/libraries&gt;
 * &lt;/relative-resources&gt;
 * </pre>
 *
 * <p>ATTENTION: This ResourceHandler only works with prefix mapping. Please make sure your application
 * uses prefix mapping ONLY (like e.g. /faces or /jsf) or at least provides the prefix mapping "/faces"
 * for the FacesServlet.</p>
 *
 * @author Jakob Korherr
 */
public class RelativeResourceHandler extends ResourceHandlerWrapper
{

    /**
     * The logger for this class.
     */
    private static final Logger log = Logger.getLogger(RelativeResourceHandler.class.getName());

    /**
     * web.xml config parameter for max expire time of resources.
     */
    public static final String RESOURCE_MAX_TIME_EXPIRES_PARAM
            = "org.apacheextras.myfaces.resourcehandler.RESOURCE_MAX_TIME_EXPIRES";
    public static final long RESOURCE_MAX_TIME_EXPIRES_DEFAULT = 604800000L;

    /**
     * web.xml config parameter for the FacesServlet mapping prefix. Default is "/faces".
     */
    public static final String FACES_SERVLET_PREFIX_PARAM
            = "org.apacheextras.myfaces.resourcehandler.FACES_SERVLET_PREFIX";

    /**
     * Default value for the FacesServlet mapping prefix.
     */
    public static final String DEFAULT_FACES_SERVLET_PREFIX = "/faces";

    /**
     * Config file for RelativeResourceHandler.
     */
    public static final String CONFIG_FILE = "META-INF/relative-resources.xml";

    public static final String REQUESTED_LOCALE_PREFIX_CACHE
            = "org.apacheextras.myfaces.resourcehandler.REQUESTED_LOCALE_PREFIX_CACHE";

    /**
     * Flag in FacesContext attribute map that indicates if we're currently handling a resource request.
     */
    public static final String HANDLING_RESOURCE_REQUEST
            = "org.apacheextras.myfaces.resourcehandler.HANDLING_RESOURCE_REQUEST";

    /**
     * Flag in FacesContext attribute map that indicates if we're currently evaluating EL expressions of a resource.
     */
    public static final String EVALUATING_RESOURCE_EL_EXPRESSIONS
            = "org.apacheextras.myfaces.resourcehandler.EVALUATING_RESOURCE_EL_EXPRESSIONS";

    /**
     * web.xml config parameter for the max cache size in
     * {@link org.apacheextras.myfaces.resourcehandler.cache.RelativeResourceCache}.
     */
    public static final String MAX_CACHE_SIZE_PARAM
             = "org.apacheextras.myfaces.resourcehandler.MAX_CACHE_SIZE";

    /**
     * Default value for config  parameter {@link RelativeResourceHandler#MAX_CACHE_SIZE_PARAM}.
     */
    public static final int DEFAULT_MAX_CACHE_SIZE = 1000;

    private ResourceHandler wrappedHandler;
    private RelativeResourceHandlerConfig config;
    private RelativeResourceResolver resourceResolver;
    private RelativeResourceCache relativeResourceCache;

    public RelativeResourceHandler(ResourceHandler wrappedHandler) throws FacesException
    {
        FacesContext facesContext = FacesContext.getCurrentInstance();

        this.wrappedHandler = wrappedHandler;
        this.config = getConfigFromProviderSpi();
        this.resourceResolver = getRelativeResourceResolverFromSpi(facesContext);
        this.relativeResourceCache = new RelativeResourceCache(
                ResourceUtils.getRelativeResourceMaxCacheSize(facesContext));
    }

    @Override
    public ResourceHandler getWrapped()
    {
        return wrappedHandler;
    }

    @Override
    public Resource createResource(String resourceName)
    {
        return createResource(resourceName, null, null);   
    }

    @Override
    public Resource createResource(String resourceName, String libraryName)
    {
        return createResource(resourceName, libraryName, null);
    }

    @Override
    public Resource createResource(String resourceName, String libraryName, final String contentType)
    {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        final ResourceId resourceId;

        // if we have no library name,
        // are currently handling a resource request
        // and do not currently evaluate EL expressions of a resource
        // then resourceName contains our (relative) path information that we need to serve the resource.
        if (libraryName == null
                && isCurrentlyHandlingResourceRequest(facesContext)
                && !isCurrentlyEvaluatingResourceElExpressions(facesContext))
        {
            resourceId = resourceResolver.calculateRelativeId(resourceName, config);

            // cache requested locale prefix for usage in #resource[''] references inside resource-files
            if (resourceId != null && resourceId.getRequestedLocalePrefix() != null)
            {
                facesContext.getAttributes().put(REQUESTED_LOCALE_PREFIX_CACHE,
                        resourceId.getRequestedLocalePrefix());
            }
        }
        else
        {
            String requestedLocalePrefix = null;  // not handling a request, thus requested locale prefix = null
            resourceId = resourceResolver.calculateRelativeId(resourceName, libraryName, requestedLocalePrefix, config);
        }

        if (resourceId != null)
        {
            // create RelativeResource (either from cache or from resource resolver)
            RelativeResource relativeResource = relativeResourceCache.get(resourceId, new Callable<RelativeResource>()
            {
                public RelativeResource call() throws Exception
                {
                    // not found in cache, create instance
                    return resourceResolver.createRelativeResource(resourceId, contentType, config);
                }
            });

            if (relativeResource != null)
            {
                if (!relativeResource.isInitialized())  // check if already initialized (fail fast w/o sync)
                {
                    synchronized (relativeResource)   // need to sync, as many threads may come here at the same time
                    {
                        if (!relativeResource.isInitialized())  // synced double check
                        {
                            // init resource synchronously
                            relativeResource.initialize(facesContext);
                        }
                    }
                }

                // use relative resource only if it really exists
                if (relativeResource.resourceExists())
                {
                    return relativeResource;
                }
            }
        }

        // use wrapped ResourceHandler (from MyFaces or Mojarra)
        return super.createResource(resourceName, libraryName, contentType);
    }

    @Override
    public void handleResourceRequest(FacesContext facesContext) throws IOException
    {
        facesContext.getAttributes().put(HANDLING_RESOURCE_REQUEST, Boolean.TRUE);
        super.handleResourceRequest(facesContext);
        facesContext.getAttributes().put(HANDLING_RESOURCE_REQUEST, Boolean.FALSE);
    }

    private boolean isCurrentlyHandlingResourceRequest(FacesContext facesContext)
    {
        return Boolean.TRUE.equals(facesContext.getAttributes().get(HANDLING_RESOURCE_REQUEST));
    }

    private boolean isCurrentlyEvaluatingResourceElExpressions(FacesContext facesContext)
    {
        return Boolean.TRUE.equals(facesContext.getAttributes().get(EVALUATING_RESOURCE_EL_EXPRESSIONS));
    }

    /**
     * Used for unit testing.
     *
     * @return
     */
    RelativeResourceHandlerConfig getConfig()
    {
        return config;
    }

    /**
     * Used for unit testing.
     *
     * @param config
     */
    void setConfig(RelativeResourceHandlerConfig config)
    {
        this.config = config;
    }

    /**
     * Uses the RelativeResourceHandlerConfigProvider SPI in order to get a RelativeResourceHandlerConfig instance.
     *
     * @return
     * @throws FacesException
     */
    private RelativeResourceHandlerConfig getConfigFromProviderSpi() throws FacesException
    {
        // use ServiceLoader to load the SPI implementation
        ServiceLoader<RelativeResourceHandlerConfigProvider> serviceLoader = ServiceLoader.load(
                RelativeResourceHandlerConfigProvider.class, ResourceUtils.getContextClassLoader());

        RelativeResourceHandlerConfigProvider configProvider = null;
        Iterator<RelativeResourceHandlerConfigProvider> iterator = serviceLoader.iterator();
        if (iterator.hasNext())
        {
            configProvider = iterator.next();

            if (iterator.hasNext())
            {
                throw new FacesException("Found more than one implementation of "
                        + RelativeResourceHandlerConfigProvider.class.getName());
            }

            log.info("Using SPI provider " + configProvider.getClass().getName() + " for RelativeResourceHandlerConfig.");
        }
        else
        {
            log.info("Using default provider for RelativeResourceHandlerConfig.");

            // use the default one
            configProvider = new DefaultRelativeResourceHandlerConfigProvider();
        }

        // get config from provider
        return configProvider.getRelativeResourceHandlerConfig();
    }

    /**
     * Uses the RelativeResourceResolverProvider SPI in order to get a RelativeResourceResolver instance.
     *
     * @return
     * @throws FacesException
     */
    private RelativeResourceResolver getRelativeResourceResolverFromSpi(FacesContext facesContext) throws FacesException
    {
        // use ServiceLoader to load the SPI implementation
        ServiceLoader<RelativeResourceResolverProvider> serviceLoader = ServiceLoader.load(
                RelativeResourceResolverProvider.class, ResourceUtils.getContextClassLoader());

        Iterator<RelativeResourceResolverProvider> iterator = serviceLoader.iterator();
        if (iterator.hasNext())
        {
            RelativeResourceResolverProvider resolverProvider = iterator.next();

            if (iterator.hasNext())
            {
                throw new FacesException("Found more than one implementation of "
                        + RelativeResourceResolverProvider.class.getName());
            }

            log.info("Using RelativeResourceResolver from SPI provider " + resolverProvider.getClass().getName());

            // get resolver from provider
            return resolverProvider.getRelativeResourceResolver(facesContext);
        }
        else
        {
            log.info("Using default RelativeResourceResolver implementation.");

            // no provider given, use default resolver
            return new DefaultRelativeResourceResolver();
        }
    }

}
