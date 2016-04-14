/*
 * Copyright 2012, Jakob Korherr
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
package org.apacheextras.myfaces.resourcehandler.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apacheextras.myfaces.resourcehandler.RelativeResource;
import org.apacheextras.myfaces.resourcehandler.ResourceId;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Cache for relative resources.
 *
 * @author Jakob Korherr
 */
public class RelativeResourceCache
{

    private Cache<ResourceId, RelativeResource> cache;

    public RelativeResourceCache(int maxCacheSize)
    {
        cache = CacheBuilder.newBuilder().maximumSize(maxCacheSize).build();
    }

    public RelativeResource get(ResourceId resourceId, Callable<RelativeResource> resourceCreator)
    {
        try
        {
            return cache.get(resourceId, resourceCreator);
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException("Exception while accessing RelativeResourceCache", e);
        }
    }

}
