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

import javax.faces.application.Resource;
import javax.faces.context.FacesContext;

/**
 * Resource specification for RelativeResourceHandler.
 *
 * @author Jakob Korherr
 */
public abstract class RelativeResource extends Resource
{

    public abstract void initialize(FacesContext facesContext);

    public abstract boolean isInitialized();

    public abstract boolean resourceExists();

    public abstract String getResourceFilePath();

    public abstract String getResourceFilePath(boolean includeLibraryName);

    public abstract String getRelativePath();

    public abstract String getRequestedLocalePrefix();

}
