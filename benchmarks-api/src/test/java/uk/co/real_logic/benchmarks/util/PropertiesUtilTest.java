/*
 * Copyright 2015-2023 Real Logic Limited.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.agrona.PropertyAction.PRESERVE;
import static org.agrona.PropertyAction.REPLACE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PropertiesUtilTest
{
    @BeforeEach
    void setUp()
    {
        System.setProperty("property1", "system_value1");
        System.setProperty("property2", "system_value2");
        System.setProperty("property3", "system_value3");
    }

    @AfterEach
    void tearDown()
    {
        System.getProperties().remove("property1");
        System.getProperties().remove("property2");
        System.getProperties().remove("property3");
    }

    @Test
    void shouldLoadPropertiesFromResource()
    {
        final Properties properties = PropertiesUtil.loadPropertiesFile(new Properties(), REPLACE, "test.properties");
        assertContains(
            properties, "property1", "resource_value1", "property2", "resource_value2", "property3", "resource_value3");
    }

    @Test
    void shouldLoadPropertiesFromFile(@TempDir final Path tempDir) throws IOException
    {
        final Path filename = tempDir.resolve("loadPropertiesFromFile.properties");
        Files.write(
            filename,
            asList("property1=file_value1", "property2=file_value2", "property3=file_value3"),
            StandardOpenOption.CREATE);

        final Properties properties = PropertiesUtil.loadPropertiesFile(new Properties(), REPLACE, filename.toString());
        assertContains(
            properties, "property1", "file_value1", "property2", "file_value2", "property3", "file_value3");
    }

    @Test
    void shouldReplaceOnLoad(@TempDir final Path tempDir) throws IOException
    {
        final Path filename1 = tempDir.resolve("loadPropertiesFromFile1.properties");
        final Path filename2 = tempDir.resolve("loadPropertiesFromFile2.properties");
        Files.write(
            filename1,
            asList("property1=file_value1", "property2=file_value2", "property3=file_value3"),
            StandardOpenOption.CREATE);
        Files.write(
            filename2,
            asList("property1=replaced_value1", "property3=replaced_value3", "property4=replaced_value4"),
            StandardOpenOption.CREATE);

        final Properties properties = PropertiesUtil.loadPropertiesFiles(
            new Properties(), REPLACE, filename1.toString(), filename2.toString());

        assertContains(
            properties,
            "property1", "replaced_value1",
            "property2", "file_value2",
            "property3", "replaced_value3",
            "property4", "replaced_value4");
    }

    @Test
    void shouldPreserveOnLoad(@TempDir final Path tempDir) throws IOException
    {
        final Path filename1 = tempDir.resolve("loadPropertiesFromFile1.properties");
        final Path filename2 = tempDir.resolve("loadPropertiesFromFile2.properties");
        Files.write(
            filename1,
            asList("property1=file_value1", "property2=file_value2", "property3=file_value3"),
            StandardOpenOption.CREATE);
        Files.write(
            filename2,
            asList("property1=replaced_value1", "property3=replaced_value3", "property4=replaced_value4"),
            StandardOpenOption.CREATE);

        final Properties properties = PropertiesUtil.loadPropertiesFiles(
            new Properties(), PRESERVE, filename1.toString(), filename2.toString());

        assertContains(
            properties,
            "property1", "file_value1",
            "property2", "file_value2",
            "property3", "file_value3",
            "property4", "replaced_value4");
    }

    @Test
    void shouldPreserveOnMergeWithSystem()
    {
        final Properties properties = new Properties();
        properties.setProperty("property1", "test_value1");
        properties.setProperty("property3", "test_value3");
        properties.setProperty("property4", "test_value4");

        PropertiesUtil.mergeWithSystemProperties(PRESERVE, properties);

        assertContains(
            System.getProperties(),
            "property1", "system_value1",
            "property2", "system_value2",
            "property3", "system_value3",
            "property4", "test_value4");
    }

    @Test
    void shouldReplaceOnMergeWithSystem()
    {
        final Properties properties = new Properties();
        properties.setProperty("property1", "test_value1");
        properties.setProperty("property3", "test_value3");
        properties.setProperty("property4", "test_value4");

        PropertiesUtil.mergeWithSystemProperties(REPLACE, properties);

        assertContains(
            System.getProperties(),
            "property1", "test_value1",
            "property2", "system_value2",
            "property3", "test_value3",
            "property4", "test_value4");
    }

    private void assertContains(final Properties properties, final String... keyOrValue)
    {
        if (0 != (keyOrValue.length % 2))
        {
            throw new IllegalArgumentException("keyOrValue must be even");
        }

        for (int i = 0; i < keyOrValue.length; i += 2)
        {
            assertEquals(keyOrValue[i + 1], properties.getProperty(keyOrValue[i]));
        }
    }
}