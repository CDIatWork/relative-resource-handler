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
package org.apacheextras.myfaces.resourcehandler;

import java.io.Serializable;

/**
 * ID class, uniquely identifying a RelativeResource.
 *
 * @author Jakob Korherr
 */
public class ResourceId implements Serializable
{

    private final String resourceName;
    private final String libraryName;
    private final String requestedLocalePrefix;

    public ResourceId(String resourceName, String libraryName, String requestedLocalePrefix)
    {
        this.resourceName = resourceName;
        this.libraryName = libraryName;
        this.requestedLocalePrefix = requestedLocalePrefix;
    }

    public String getResourceName()
    {
        return resourceName;
    }

    public String getLibraryName()
    {
        return libraryName;
    }

    public String getRequestedLocalePrefix()
    {
        return requestedLocalePrefix;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof ResourceId))
        {
            return false;
        }

        ResourceId that = (ResourceId) o;

        if (libraryName != null ? !libraryName.equals(that.libraryName) : that.libraryName != null)
        {
            return false;
        }
        if (requestedLocalePrefix != null ? !requestedLocalePrefix.equals(that.requestedLocalePrefix) : that.requestedLocalePrefix != null)
        {
            return false;
        }
        if (resourceName != null ? !resourceName.equals(that.resourceName) : that.resourceName != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = resourceName != null ? resourceName.hashCode() : 0;
        result = 31 * result + (libraryName != null ? libraryName.hashCode() : 0);
        result = 31 * result + (requestedLocalePrefix != null ? requestedLocalePrefix.hashCode() : 0);
        return result;
    }

}
