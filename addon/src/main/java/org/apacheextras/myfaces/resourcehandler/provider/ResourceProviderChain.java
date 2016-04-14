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
package org.apacheextras.myfaces.resourcehandler.provider;

import org.apacheextras.myfaces.resourcehandler.RelativeResource;

import javax.faces.context.FacesContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

/**
 * A ResourceProvider implementation containing a chain of resource providers.
 * The chain is processed sequentially in order to find the first non-null return value.
 *
 * @author Jakob Korherr
 */
public class ResourceProviderChain implements ResourceProvider
{

    private List<ResourceProvider> resourceProviderChain;

    public ResourceProviderChain(List<ResourceProvider> resourceProviderChain)
    {
        this.resourceProviderChain = resourceProviderChain;
    }

    public URL getUrl(FacesContext facesContext, RelativeResource relativeResource)
    {
        URL url = null;

        for (ResourceProvider resourceProvider : resourceProviderChain)
        {
            url = resourceProvider.getUrl(facesContext, relativeResource);
            if (url != null)
            {
                break;
            }
        }

        return url;
    }

    public InputStream getInputStream(FacesContext facesContext, RelativeResource relativeResource) throws IOException
    {
        InputStream inputStream = null;

        for (ResourceProvider resourceProvider : resourceProviderChain)
        {
            inputStream = resourceProvider.getInputStream(facesContext, relativeResource);
            if (inputStream != null)
            {
                break;
            }
        }

        return inputStream;
    }
}
