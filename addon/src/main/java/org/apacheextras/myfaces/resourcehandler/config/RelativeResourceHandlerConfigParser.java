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
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.faces.context.FacesContext;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * XML-parser for RelativeResourceHandlerConfig.
 *
 * @author Jakob Korherr
 */
public class RelativeResourceHandlerConfigParser
{

    /**
     * The logger for this class.
     */
    private static final Logger log = Logger.getLogger(RelativeResourceHandlerConfigParser.class.getName());

    /**
     * The URI for the W3C XMLSchema instance.
     */
    private static final String W3C_XML_SCHEMA_URI = "http://www.w3.org/2001/XMLSchema";

    /**
     * The XSD schema file for our config file, relative-resources.xml
     */
    private static final String RELATIVE_RESOURCES_XSD = "META-INF/relative-resources.xsd";

    /**
     * The validator for our XSD.
     */
    private Validator validator;

    public RelativeResourceHandlerConfigParser()
    {
        initializeXsdValidator();
    }

    public void parseClasspathResource(String resource, RelativeResourceHandlerConfig config)
            throws IOException, XMLStreamException
    {
        ClassLoader classLoader = ResourceUtils.getContextClassLoader();
        Enumeration<URL> configUrls = classLoader.getResources(resource);
        if (configUrls != null)
        {
            while (configUrls.hasMoreElements())
            {
                parseUrl(configUrls.nextElement(), config);
            }
        }
    }

    public void parseUrl(URL url, RelativeResourceHandlerConfig config)
            throws IOException, XMLStreamException
    {
        if (config.isParsedAlready(url))
        {
            return;
        }

        config.addParsedLocation(url);

        // validate config file via XML schema
        if (shouldValidateSchema())
        {
            try
            {
                validateSchema(url);
            }
            catch (SAXParseException validationException)
            {
                throw new IllegalStateException("Config file " + url.toExternalForm()
                        + " violates XSD schema definition at line "
                        + validationException.getLineNumber()
                        + " in column " + validationException.getColumnNumber() + ". "
                        + "Error message: " + validationException.getMessage());
            }
            catch (SAXException validationException)
            {
                throw new IllegalStateException("Config file violates XSD schema definition. "
                        + "Error message: " + validationException.getMessage());
            }
        }

        XMLStreamReader streamReader = null;
        InputStream urlInputStream = url.openStream();
        try
        {
            // create XMLInputFactory
            XMLInputFactory factory = XMLInputFactory.newInstance();
            streamReader = factory.createXMLStreamReader(urlInputStream);

            while (streamReader.hasNext())
            {
                streamReader.next();

                if (streamReader.isStartElement())
                {
                    if ("library".equals(streamReader.getLocalName()))
                    {
                        // read library and add it to config
                        Library library = readLibrary(streamReader);
                        if (library != null)
                        {
                            config.addLibrary(library);
                        }
                    }
                    else if ("url-version".equals(streamReader.getLocalName()))
                    {
                        // read url-version and set it on config
                        config.setUrlVersion(readUrlVersion(streamReader));
                    }
                    else if ("gzip-enabled".equals(streamReader.getLocalName()))
                    {
                        // read gzip-enabled and set it on config
                        config.setGzipEnabled(readGzipEnabled(streamReader));
                    }
                    else if ("locale-support-enabled".equals(streamReader.getLocalName()))
                    {
                        // read locale-support-enabled and set it on config
                        config.setLocaleSupportEnabled(readLocaleSupportEnabled(streamReader));
                    }
                }
            }
        }
        finally
        {
            // close Reader
            if (streamReader != null)
            {
                streamReader.close();
            }
            // close InputStream or URL
            if (urlInputStream != null)
            {
                urlInputStream.close();
            }
        }
    }

    private Library readLibrary(XMLStreamReader streamReader) throws XMLStreamException
    {
        final String libraryName = streamReader.getAttributeValue(null, "name");
        if (libraryName == null)
        {
            throw new XMLStreamException("<library> element without name attribute", streamReader.getLocation());
        }

        final String forProjectStage = streamReader.getAttributeValue(null, "project-stage");

        Library.LocationType locationType = null;
        StringBuilder sbLocation = new StringBuilder();
        StringBuilder sbFileMask = null;
        String localName = null;
        boolean insideElEvaluation = false;
        List<String> elEvaluationFileMasks = new LinkedList<String>();

        while (streamReader.hasNext())
        {
            streamReader.next();

            if (streamReader.isStartElement())
            {
                localName = streamReader.getLocalName();

                if ("location".equals(localName))
                {
                    if (locationType != null)
                    {
                        throw new XMLStreamException("Only one <location> element is allowed inside <library>",
                                streamReader.getLocation());
                    }

                    String locationTypeString = streamReader.getAttributeValue(null, "type");
                    if (locationTypeString == null)
                    {
                        throw new XMLStreamException("<location> element without type attribute",
                                streamReader.getLocation());
                    }

                    // parse location type
                    try
                    {
                        locationType = Library.LocationType.valueOf(locationTypeString.toUpperCase());
                    }
                    catch (IllegalArgumentException e)
                    {
                        throw new XMLStreamException("Invalid location type attribute " + locationTypeString,
                                streamReader.getLocation());
                    }
                }
                else if ("el-evaluation".equals(localName))
                {
                    insideElEvaluation = true;
                }
                else if ("file-mask".equals(localName))
                {
                    if (!insideElEvaluation)
                    {
                        throw new XMLStreamException("<file-mask> is only allowed inside of <el-evaluation>",
                            streamReader.getLocation());
                    }

                    sbFileMask = new StringBuilder();
                }
                else
                {
                    throw new XMLStreamException("Invalid child element <" + localName + "> of <library>",
                            streamReader.getLocation());
                }
            }
            else if (streamReader.isCharacters())
            {
                if ("location".equals(localName))
                {
                    // use StringBuilder.append(), b/c characters can get called multiple times
                    sbLocation.append(streamReader.getText());
                }
                else if ("file-mask".equals(localName))
                {
                    // use StringBuilder.append(), b/c characters can get called multiple times
                    sbFileMask.append(streamReader.getText());
                }
            }
            else if (streamReader.isEndElement())
            {
                localName = streamReader.getLocalName();

                if ("library".equals(localName))
                {
                    String location = sbLocation.toString().trim();
                    if (location.length() == 0)
                    {
                        location = null;
                    }

                    if (Library.LocationType.EXTERNAL.equals(locationType) && !elEvaluationFileMasks.isEmpty())
                    {
                        throw new XMLStreamException("El-evaluation is not available for external resources",
                                streamReader.getLocation());
                    }

                    if (forProjectStage != null && !forProjectStage.equals(
                            FacesContext.getCurrentInstance().getApplication().getProjectStage().name()))
                    {
                        return null;
                    }

                    return new Library(libraryName, locationType, location, elEvaluationFileMasks);
                }
                else if ("el-evaluation".equals(localName))
                {
                    insideElEvaluation = false;
                }
                else if ("file-mask".equals(localName))
                {
                    elEvaluationFileMasks.add(sbFileMask.toString().trim());
                }
                else if (!"location".equals(localName))
                {
                    throw new XMLStreamException("Invalid end element <" + localName + "> inside <library>",
                            streamReader.getLocation());
                }

                // set localName to null to prevent matching the following text section
                // between this end-element and the next start-element
                localName = null;
            }
        }

        // should never be reached
        throw new XMLStreamException("Could not find end element of library");
    }

    private String readUrlVersion(XMLStreamReader streamReader) throws XMLStreamException
    {
        return readDataElement("url-version", streamReader);
    }

    private boolean readGzipEnabled(XMLStreamReader streamReader) throws XMLStreamException
    {
        String gzipEnabledString = readDataElement("gzip-enabled", streamReader);

        return Boolean.parseBoolean(gzipEnabledString);
    }

    private boolean readLocaleSupportEnabled(XMLStreamReader streamReader) throws XMLStreamException
    {
        String localeSupportEnabledString = readDataElement("locale-support-enabled", streamReader);

        return Boolean.parseBoolean(localeSupportEnabledString);
    }

    private String readDataElement(String tagName, XMLStreamReader streamReader) throws XMLStreamException
    {
        StringBuilder sbValue = new StringBuilder();

        while (streamReader.hasNext())
        {
            streamReader.next();

            if (streamReader.isStartElement())
            {
                throw new XMLStreamException("<" + tagName + "> must not contain any start element",
                        streamReader.getLocation());
            }
            else if (streamReader.isCharacters())
            {
                // use StringBuilder.append(), b/c characters can get called multiple times
                sbValue.append(streamReader.getText());
            }
            else if (streamReader.isEndElement())
            {
                String localName = streamReader.getLocalName();
                if (tagName.equals(localName))
                {
                    // we're done
                    break;
                }

                throw new XMLStreamException("Invalid end element <" + localName + ">, expected </"+ tagName + ">",
                        streamReader.getLocation());
            }
        }

        return sbValue.toString();
    }

    private void initializeXsdValidator()
    {
        // lookup a factory for the W3C XML Schema language
        SchemaFactory factory = SchemaFactory.newInstance(W3C_XML_SCHEMA_URI);

        // compile the schema.
        ClassLoader classLoader = ResourceUtils.getContextClassLoader();
        URL schemaUrl = classLoader.getResource(RELATIVE_RESOURCES_XSD);
        Schema schema;
        try
        {
            schema = factory.newSchema(schemaUrl);

            // get a validator from the schema.
            validator = schema.newValidator();
        }
        catch (SAXException e)
        {
            log.warning("Could not initialize XSD schema validator, " +
                    "thus relative-resources.xml will not be validated before parsing.");
        }
    }

    private boolean shouldValidateSchema()
    {
        return (validator != null);
    }

    private void validateSchema(URL url) throws SAXException, IOException
    {
        InputStream inputStream = url.openStream();
        try
        {
            // validate the xml
            validator.validate(new StreamSource(inputStream));
        }
        finally
        {
            if (inputStream != null)
            {
                inputStream.close();
            }
        }
    }

}
