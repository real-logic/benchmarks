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
package uk.co.real_logic.benchmarks.remote;

import org.HdrHistogram.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static uk.co.real_logic.benchmarks.remote.PersistedHistogram.*;
import static uk.co.real_logic.benchmarks.remote.PersistedHistogram.Status.FAIL;
import static uk.co.real_logic.benchmarks.remote.PersistedHistogram.Status.OK;

class PersistedHistogramTest
{
    @ParameterizedTest
    @MethodSource("histograms")
    void saveToFileThrowsNullPointerExceptionIfOutputDirectoryIsNull(
        final Function<Path, PersistedHistogram> histogramFactory, final @TempDir Path tempDir)
    {
        try (PersistedHistogram histogram = histogramFactory.apply(tempDir))
        {
            assertThrows(NullPointerException.class, () -> histogram.saveToFile(null, "my-file", OK));
        }
    }

    @ParameterizedTest
    @MethodSource("histograms")
    void saveToFileThrowsNullPointerExceptionIfNamePrefixIsNull(
        final Function<Path, PersistedHistogram> histogramFactory, final @TempDir Path tempDir)
    {
        try (PersistedHistogram histogram = histogramFactory.apply(tempDir))
        {
            assertThrows(NullPointerException.class, () -> histogram.saveToFile(tempDir, null, OK));
        }
    }

    @ParameterizedTest
    @MethodSource("histograms")
    void saveToFileThrowsIllegalArgumentExceptionIfNamePrefixIsEmpty(
        final Function<Path, PersistedHistogram> histogramFactory, final @TempDir Path tempDir)
    {
        try (PersistedHistogram histogram = histogramFactory.apply(tempDir))
        {
            assertThrows(IllegalArgumentException.class, () -> histogram.saveToFile(tempDir, "", OK));
        }
    }

    @ParameterizedTest
    @MethodSource("histograms")
    void saveToFileThrowsIllegalArgumentExceptionIfNamePrefixIsBlank(
        final Function<Path, PersistedHistogram> histogramFactory, final @TempDir Path tempDir)
    {
        try (PersistedHistogram histogram = histogramFactory.apply(tempDir))
        {
            assertThrows(IllegalArgumentException.class, () -> histogram.saveToFile(tempDir, " \t  \n  ", OK));
        }
    }

    @ParameterizedTest
    @MethodSource("histograms")
    void saveToFileThrowsIOExceptionIfSaveFails(
        final Function<Path, PersistedHistogram> histogramFactory, final @TempDir Path tempDir) throws IOException
    {
        try (PersistedHistogram histogram = histogramFactory.apply(tempDir))
        {
            final Path rootFile = Files.createFile(tempDir.resolve("my.txt"));

            final ValueRecorder valueRecorder = histogram.valueRecorder();
            valueRecorder.recordValue(2);
            valueRecorder.recordValue(4);

            assertThrows(IOException.class, () -> histogram.saveToFile(rootFile, "ignore", OK));
        }
    }

    @ParameterizedTest
    @MethodSource("histograms")
    void saveToFileCreatesNewFileWithIndexZero(
        final Function<Path, PersistedHistogram> histogramFactory, final @TempDir Path tempDir) throws IOException
    {
        final Histogram expectedHistogram = new Histogram(3);
        final Path file;

        try (PersistedHistogram histogram = histogramFactory.apply(tempDir))
        {
            Files.createFile(tempDir.resolve("another-one-13.hdr"));

            final ValueRecorder valueRecorder = histogram.valueRecorder();
            valueRecorder.recordValue(100);
            expectedHistogram.recordValue(100);
            valueRecorder.recordValue(1000);
            expectedHistogram.recordValue(1000);
            valueRecorder.recordValue(250);
            expectedHistogram.recordValue(250);

            file = histogram.saveToFile(tempDir, "test-histogram", FAIL);
        }

        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertEquals(
            "test-histogram" + INDEX_SEPARATOR + "0.hdr" + FAILED_FILE_SUFFIX,
            file.getFileName().toString());
        final Histogram savedHistogram = readHistogram(file);
        assertEquals(expectedHistogram, savedHistogram);
    }

    @ParameterizedTest
    @MethodSource("histograms")
    void saveToFileCreatesNewFileByIncrementExistingMaxIndex(
        final Function<Path, PersistedHistogram> histogramFactory, final @TempDir Path tempDir) throws IOException
    {
        final Histogram expectedHistogram = new Histogram(3);
        final Path file;
        try (PersistedHistogram histogram = histogramFactory.apply(tempDir))
        {
            Files.createFile(tempDir.resolve("another_one-13.hdr"));
            Files.createFile(tempDir.resolve("another_one-5.hdr.FAIL"));
            Files.createFile(tempDir.resolve("another_one" + AGGREGATE_FILE_SUFFIX));

            final ValueRecorder valueRecorder = histogram.valueRecorder();
            valueRecorder.recordValue(2);
            expectedHistogram.recordValue(2);
            valueRecorder.recordValue(4);
            expectedHistogram.recordValue(4);

            file = histogram.saveToFile(tempDir, "another_one", OK);
        }

        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertEquals("another_one-14.hdr", file.getFileName().toString());

        final Histogram savedHistogram = readHistogram(file);
        assertEquals(expectedHistogram, savedHistogram);
    }

    @ParameterizedTest
    @MethodSource("histograms")
    void recordManyValuesOverMultipleSecondsAndReset(
        final Function<Path, PersistedHistogram> histogramFactory, final @TempDir Path tempDir)
        throws IOException, InterruptedException
    {
        final Histogram expectedHistogram = new Histogram(3);
        final Path file;
        try (PersistedHistogram histogram = histogramFactory.apply(tempDir))
        {
            Files.createFile(tempDir.resolve("another_one" + AGGREGATE_FILE_SUFFIX));

            final ValueRecorder valueRecorder = histogram.valueRecorder();
            final Random r = new Random();

            for (int i = 0; i < 1000; i++)
            {
                for (int j = 0; j < 5; j++)
                {
                    final int value = r.nextInt(1000);
                    valueRecorder.recordValue(value);
                }
                Thread.sleep(1);
            }

            histogram.reset();

            for (int i = 0; i < 1000; i++)
            {
                for (int j = 0; j < 5; j++)
                {
                    final int value = r.nextInt(1000);
                    valueRecorder.recordValue(value);
                    expectedHistogram.recordValue(value);
                }
                Thread.sleep(1);
            }

            file = histogram.saveToFile(tempDir, "another_one", FAIL);
        }

        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertEquals("another_one-0.hdr.FAIL", file.getFileName().toString());

        final Histogram savedHistogram = readHistogram(file);
        assertEquals(expectedHistogram.getTotalCount(), savedHistogram.getTotalCount());
        assertEquals(expectedHistogram, savedHistogram);
    }

    static Histogram readHistogram(final Path file) throws FileNotFoundException
    {
        final List<EncodableHistogram> histograms = new ArrayList<>();
        try (HistogramLogReader logReader = new HistogramLogReader(file.toFile()))
        {
            while (logReader.hasNext())
            {
                histograms.add(logReader.nextIntervalHistogram());
            }
        }

        assertEquals(1, histograms.size());

        return (Histogram)histograms.get(0);
    }

    private static Stream<Arguments> histograms()
    {
        return Stream.of(arguments(new SingleHistogramFactory()), arguments(new LoggingHistogramFactory()));
    }

    private static class SingleHistogramFactory implements Function<Path, PersistedHistogram>
    {
        public PersistedHistogram apply(final Path path)
        {
            return new SinglePersistedHistogram(new Histogram(3));
        }

        public String toString()
        {
            return "Single";
        }
    }

    private static class LoggingHistogramFactory implements Function<Path, PersistedHistogram>
    {
        public PersistedHistogram apply(final Path path)
        {
            try
            {
                return new LoggingPersistedHistogram(path, new SingleWriterRecorder(3));
            }
            catch (final IOException ex)
            {
                throw new RuntimeException(ex);
            }
        }

        public String toString()
        {
            return "Logged";
        }
    }
}
