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

import java.io.Serializable;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Config for the relative resource handler.
 *
 * @author Jakob Korherr
 */
public class RelativeResourceHandlerConfig implements Serializable
{

    public static final String URL_VERSION_DEFAULT = "1";
    public static final boolean GZIP_ENABLED_DEFAULT = true;
    public static final boolean LOCALE_SUPPORT_ENABLED_DEFAULT = true;

    private Map<String, Library> libraries;
    private String urlVersion;
    private Boolean gzipEnabled;
    private Boolean localeSupportEnabled;

    //workaround for embedded containers on win (upper- vs. lower-case drive-letters)
    private Set<String> parsedPaths;

    /**
     * Creates and fills the config (with the help of the related parser).
     */
    public RelativeResourceHandlerConfig()
    {
        // use concurrent hash map to avoid concurrency problems
        // NOTE that multiple threads may simultaneously serve/create resources
        libraries = new ConcurrentHashMap<String, Library>();
        parsedPaths = new CopyOnWriteArraySet<String>();
    }

    /**
     * Adds the given library to the libraries which are handled by the RelativeResourceHandler.
     * Should only be called at startup (e.g. by the config parser).
     *
     * @param library
     * @throws IllegalArgumentException if a library with the same name does already exist.
     */
    public void addLibrary(Library library) throws IllegalArgumentException
    {
        if (isRelativeLibrary(library.getName()))
        {
            throw new IllegalArgumentException("Config already contains a library named " + library.getName());
        }

        libraries.put(library.getName(), library);
    }

    /**
     * Check if the given library should be handled by the RelativeResourceHandler.
     *
     * @param name
     * @return
     */
    public boolean isRelativeLibrary(String name)
    {
        return libraries.containsKey(ResourceUtils.trimSlashes(name));
    }

    /**
     * Returns the library instance with the given name if exists, or null otherwise.
     *
     * @param name
     * @return
     */
    public Library getLibrary(String name)
    {
        return libraries.get(ResourceUtils.trimSlashes(name));
    }

    /**
     * Returns the urlVersion property of this config, or URL_VERSION_DEFAULT if no url-version has been set.
     *
     * @return
     */
    public String getUrlVersion()
    {
        if (urlVersion == null)
        {
            return URL_VERSION_DEFAULT;
        }
        return urlVersion;
    }

    /**
     * Sets the urlVersion property of this config.
     *
     * @param urlVersion
     * @throws IllegalArgumentException if a different urlVersion has already been set.
     */
    public void setUrlVersion(String urlVersion) throws IllegalArgumentException
    {
        if (this.urlVersion != null && !this.urlVersion.equals(urlVersion))
        {
            throw new IllegalArgumentException("urlVersion has already been set to a different value.");
        }
        if (urlVersion == null || urlVersion.length() == 0)
        {
            throw new IllegalArgumentException("urlVersion must not be empty");
        }
        if (urlVersion.contains("/"))
        {
            throw new IllegalArgumentException("urlVersion may not contain slashes.");
        }
        if (urlVersion.contains(" "))
        {
            throw new IllegalArgumentException("urlVersion may not contain white spaces.");
        }
        this.urlVersion = urlVersion;
    }

    /**
     * Returns the gzipEnabled property of this config, or GZIP_ENABLED_DEFAULT if no value has been set.
     *
     * @return
     */
    public boolean isGzipEnabled()
    {
        if (gzipEnabled == null)
        {
            return GZIP_ENABLED_DEFAULT;
        }
        return gzipEnabled;
    }

    /**
     * Sets the gzipEnabled property of this config.
     *
     * @param gzipEnabled
     * @throws IllegalArgumentException if a different value for gzipEnabled has already been set.
     */
    public void setGzipEnabled(boolean gzipEnabled) throws IllegalArgumentException
    {
        if (this.gzipEnabled != null && this.gzipEnabled != gzipEnabled)
        {
            throw new IllegalArgumentException("gzipEnabled has already been set to a different value.");
        }
        this.gzipEnabled = gzipEnabled;
    }

    /**
     * Returns the localeSupportEnabled property of this config,
     * or LOCALE_SUPPORT_ENABLED_DEFAULT if no value has been set.
     *
     * @return
     */
    public boolean isLocaleSupportEnabled()
    {
        if (localeSupportEnabled == null)
        {
            return LOCALE_SUPPORT_ENABLED_DEFAULT;
        }
        return localeSupportEnabled;
    }

    /**
     * Sets the localeSupportEnabled property of this config.
     *
     * @param localeSupportEnabled
     * @throws IllegalArgumentException if a different value for localeSupportEnabled has already been set.
     */
    public void setLocaleSupportEnabled(boolean localeSupportEnabled) throws IllegalArgumentException
    {
        if (this.localeSupportEnabled != null && this.localeSupportEnabled != localeSupportEnabled)
        {
            throw new IllegalArgumentException("localeSupportEnabled has already been set to a different value.");
        }
        this.localeSupportEnabled = localeSupportEnabled;
    }

    public boolean isParsedAlready(URL url)
    {
        return this.parsedPaths.contains(url.toExternalForm().toLowerCase());
    }

    public void addParsedLocation(URL url)
    {
        this.parsedPaths.add(url.toExternalForm().toLowerCase());
    }
}
