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

import org.apacheextras.myfaces.resourcehandler.RelativeResourceHandler;
import org.apacheextras.myfaces.resourcehandler.spi.RelativeResourceHandlerConfigProvider;

import javax.faces.FacesException;

/**
 * Default implementation of the RelativeResourceHandlerConfigProvider SPI.
 * It simply parses the default config file in order to obtain a RelativeResourceHandlerConfig instance.
 *
 * @author Jakob Korherr
 */
public class DefaultRelativeResourceHandlerConfigProvider implements RelativeResourceHandlerConfigProvider
{

    public RelativeResourceHandlerConfig getRelativeResourceHandlerConfig() throws FacesException
    {
        // create an empty config
        RelativeResourceHandlerConfig config = new RelativeResourceHandlerConfig();

        // parse the standard config file
        RelativeResourceHandlerConfigParser configParser = new RelativeResourceHandlerConfigParser();
        try
        {
            configParser.parseClasspathResource(RelativeResourceHandler.CONFIG_FILE, config);
        }
        catch (Exception e)
        {
            throw new FacesException("Could not parse config file " + RelativeResourceHandler.CONFIG_FILE, e);
        }

        return config;
    }

}
