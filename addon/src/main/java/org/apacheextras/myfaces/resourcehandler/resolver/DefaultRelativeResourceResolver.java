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
package org.apacheextras.myfaces.resourcehandler.resolver;

import org.apacheextras.myfaces.resourcehandler.RelativeResource;
import org.apacheextras.myfaces.resourcehandler.RelativeResourceImpl;
import org.apacheextras.myfaces.resourcehandler.ResourceId;
import org.apacheextras.myfaces.resourcehandler.ResourceUtils;
import org.apacheextras.myfaces.resourcehandler.config.RelativeResourceHandlerConfig;

/**
 * Default implementation of RelativeResourceResolver.
 *
 * @author Jakob Korherr
 */
public class DefaultRelativeResourceResolver implements RelativeResourceResolver
{

    public RelativeResource createRelativeResource(ResourceId resourceId, String contentType,
                                                   RelativeResourceHandlerConfig config)
    {
        // Return default impl of RelativeResource w/ the information given in resourceId
        return new RelativeResourceImpl(
                resourceId.getResourceName(),
                config.getLibrary(resourceId.getLibraryName()),
                contentType,
                resourceId.getRequestedLocalePrefix(),
                config.isGzipEnabled(),
                config.isLocaleSupportEnabled(),
                config.getUrlVersion());
    }

    public ResourceId calculateRelativeId(String path, RelativeResourceHandlerConfig config)
    {
        // extract the libraryName and the locale from the request path
        // --> valid resource url: 1/de/library/resources/style.css

        String libraryName = null;
        String requestedLocalePrefix = null;

        // trim any slashes at begin or end
        String resourceName = ResourceUtils.trimSlashes(path);

        // skip version in url (first part, only there to avoid cache problems on updates)
        final int versionSlash = resourceName.indexOf('/');
        if (versionSlash != -1)
        {
            String resourceNameToParse = resourceName.substring(versionSlash + 1);

            // parse locale only if locale-support is enabled
            if (config.isLocaleSupportEnabled())
            {
                // locale (chars before the first '/' in resourceNameToParse)
                final int firstSlash = resourceNameToParse.indexOf('/');
                if (firstSlash != -1)
                {
                    requestedLocalePrefix = resourceNameToParse.substring(0, firstSlash);
                    resourceNameToParse = resourceNameToParse.substring(firstSlash + 1);
                }
            }

            // parse libraryName (after version/locale to next slash)
            final int firstSlash = resourceNameToParse.indexOf('/');
            if (firstSlash != -1) {
                // we have a libraryName - get it!
                libraryName = resourceNameToParse.substring(0, firstSlash);
                resourceNameToParse = resourceNameToParse.substring(firstSlash + 1);
            }

            // the rest is the resourceName
            resourceName = resourceNameToParse;
        }

        // create the resource id
        return calculateRelativeId(resourceName, libraryName, requestedLocalePrefix, config);
    }

    public ResourceId calculateRelativeId(String resourceName, String libraryName,
                                          String requestedLocalePrefix,
                                          RelativeResourceHandlerConfig config)
    {
        if (libraryName != null)
        {
            // trim any slashes at beginning or end or libraryName
            libraryName = ResourceUtils.trimSlashes(libraryName);

            if (!libraryName.contains("/") && config.isRelativeLibrary(libraryName))
            {
                // create resource id
                return new ResourceId(resourceName, libraryName, requestedLocalePrefix);
            }
        }

        return null;
    }

}
