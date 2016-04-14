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
import org.apacheextras.myfaces.resourcehandler.ResourceId;
import org.apacheextras.myfaces.resourcehandler.config.RelativeResourceHandlerConfig;

import javax.faces.FacesWrapper;

/**
 * Wrapper for RelativeResourceResolver.
 *
 * @author Jakob Korherr
 */
public abstract class RelativeResourceResolverWrapper
        implements RelativeResourceResolver, FacesWrapper<RelativeResourceResolver>
{

    private RelativeResourceResolver wrapped;

    public RelativeResourceResolverWrapper(RelativeResourceResolver wrapped)
    {
        this.wrapped = wrapped;
    }

    public RelativeResourceResolver getWrapped()
    {
        return wrapped;
    }

    public RelativeResource createRelativeResource(ResourceId resourceId, String contentType,
                                                   RelativeResourceHandlerConfig config)
    {
        return getWrapped().createRelativeResource(resourceId, contentType, config);
    }

    public ResourceId calculateRelativeId(String path, RelativeResourceHandlerConfig config)
    {
        return getWrapped().calculateRelativeId(path, config);
    }

    public ResourceId calculateRelativeId(String resourceName, String libraryName,
                                          String requestedLocalePrefix,
                                          RelativeResourceHandlerConfig config)
    {
        return getWrapped().calculateRelativeId(resourceName, libraryName, requestedLocalePrefix, config);
    }
}
