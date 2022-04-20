/*
 * Copyright 2015-2022 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.benchmarks.util;

import org.agrona.PropertyAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public class PropertiesUtil
{
    /**
     * Load system properties from a given filename or url.
     * <p>
     * File is first searched for in resources using the system {@link ClassLoader},
     * then file system, then URL. All are loaded if multiples found.
     *
     * @param properties to fill with values.
     * @param propertyAction to take with each loaded property.
     * @param filenameOrUrl  that holds properties.
     * @return the properties collection filled with values.
     */
    public static Properties loadPropertiesFile(
        final Properties properties,
        final PropertyAction propertyAction,
        final String filenameOrUrl)
    {
        final URL resource = ClassLoader.getSystemClassLoader().getResource(filenameOrUrl);
        if (null != resource)
        {
            try (InputStream in = resource.openStream())
            {
                loadProperties(properties, propertyAction, in);
            }
            catch (final Exception ignore)
            {
            }
        }

        final File file = new File(filenameOrUrl);
        if (file.exists())
        {
            try (InputStream in = new FileInputStream(file))
            {
                loadProperties(properties, propertyAction, in);
            }
            catch (final Exception ignore)
            {
            }
        }

        try (InputStream in = new URL(filenameOrUrl).openStream())
        {
            loadProperties(properties, propertyAction, in);
        }
        catch (final Exception ignore)
        {
        }

        return properties;
    }

    /**
     * Load system properties from a given set of filenames or URLs. File is first searched for in resources using the
     * system {@link ClassLoader}, then file system, then URL. All are loaded if multiples found.
     *
     * @param properties to fill with values.
     * @param propertyAction  to take with each loaded property.
     * @param filenamesOrUrls that holds properties.
     * @return the properties collection filled with values.
     */
    public static Properties loadPropertiesFiles(
        final Properties properties,
        final PropertyAction propertyAction,
        final String... filenamesOrUrls)
    {
        for (final String filenameOrUrl : filenamesOrUrls)
        {
            loadPropertiesFile(properties, propertyAction, filenameOrUrl);
        }
        return properties;
    }

    /**
     * Merge a set of properties with the system properties. Use the supplied action to determine the behaviour
     * when a property already exists.
     * @param propertyAction action to take on existing property.
     * @param properties properties to merge into system.
     */
    public static void mergeWithSystemProperties(final PropertyAction propertyAction, final Properties properties)
    {
        mergeWithProperties(System.getProperties(), propertyAction, properties);
    }

    private static void loadProperties(
        final Properties existingProperties,
        final PropertyAction propertyAction,
        final InputStream in) throws IOException
    {
        final Properties properties = new Properties();
        properties.load(in);

        mergeWithProperties(existingProperties, propertyAction, properties);
    }

    private static void mergeWithProperties(
        final Properties existingProperties,
        final PropertyAction propertyAction,
        final Properties properties)
    {
        properties.forEach(
            (k, v) ->
            {
                switch (propertyAction)
                {
                    case PRESERVE:
                        if (!existingProperties.containsKey(k))
                        {
                            existingProperties.setProperty((String)k, (String)v);
                        }
                        break;

                    default:
                    case REPLACE:
                        existingProperties.setProperty((String)k, (String)v);
                        break;
                }
            });
    }
}
