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
package org.apacheextras.myfaces.resourcehandler.config;

import org.apacheextras.myfaces.resourcehandler.ResourceUtils;
import org.apacheextras.myfaces.resourcehandler.provider.ResourceProvider;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * DTO representing a library for the relative resource handler.
 *
 * @author Jakob Korherr
 */
public class Library implements Serializable
{

    public static enum LocationType
    {
        CLASSPATH, WEBAPP, EXTERNAL
    }

    private final String name;
    private final LocationType locationType;
    private final String location;
    private List<String> elEvaluationFileMasks;
    private ResourceProvider resourceProvider;

    public Library(String name)
    {
        this(name, null, null, new LinkedList<String>());
    }

    public Library(String name, LocationType locationType, String location, List<String> elEvaluationFileMasks)
    {
        name = ResourceUtils.trimSlashes(name);
        if (name.contains("/"))
        {
            throw new IllegalArgumentException("Library name must not contain a slash. "
                    + "See JSF 2.1 specification, section 2.6.1.3 Resource Identifiers, or "
                    + "http://code.google.com/a/apache-extras.org/p/relative-resource-handler/issues/detail?id=9 "
                    + "for details about this problem.");
        }

        this.name = name;
        this.locationType = locationType;
        this.location = location;
        this.elEvaluationFileMasks = elEvaluationFileMasks;
    }

    public String getName()
    {
        return name;
    }

    public LocationType getLocationType()
    {
        return locationType;
    }

    public String getLocation()
    {
        return location;
    }

    public List<String> getElEvaluationFileMasks()
    {
        return elEvaluationFileMasks;
    }

    public ResourceProvider getResourceProvider()
    {
        return resourceProvider;
    }

    public void setResourceProvider(ResourceProvider resourceProvider)
    {
        this.resourceProvider = resourceProvider;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof Library))
        {
            return false;
        }

        Library library = (Library) o;

        if (name != null ? !name.equals(library.name) : library.name != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return name != null ? name.hashCode() : 0;
    }

}
