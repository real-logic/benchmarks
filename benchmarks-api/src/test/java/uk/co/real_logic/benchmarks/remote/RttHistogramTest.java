/*
 * Copyright 2015-2020 Real Logic Limited.
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

import org.HdrHistogram.EncodableHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.benchmarks.remote.RttHistogram.AGGREGATE_FILE_SUFFIX;

class RttHistogramTest
{
    private final Histogram histogram = mock(Histogram.class);
    private final RttHistogram rttHistogram = new RttHistogram(histogram);

    @Test
    void reset()
    {
        rttHistogram.reset();

        verify(histogram).reset();
        verifyNoMoreInteractions(histogram);
    }

    @Test
    void recordValue()
    {
        final long value = 131;

        rttHistogram.recordValue(value);

        verify(histogram).recordValue(value);
        verifyNoMoreInteractions(histogram);
    }

    @Test
    void outputPercentileDistribution()
    {
        final PrintStream out = System.out;
        final double scaleRatio = -2.5;

        rttHistogram.outputPercentileDistribution(out, scaleRatio);

        verify(histogram).outputPercentileDistribution(out, scaleRatio);
        verifyNoMoreInteractions(histogram);
    }

    @Test
    void saveToFileThrowsNullPointerExceptionIfOutputDirectoryIsNull()
    {
        assertThrows(NullPointerException.class, () -> rttHistogram.saveToFile(null, "my-file"));
    }

    @Test
    void saveToFileThrowsNullPointerExceptionIfNamePrefixIsNull(final @TempDir Path tempDir)
    {
        assertThrows(NullPointerException.class, () -> rttHistogram.saveToFile(tempDir, null));
    }

    @Test
    void saveToFileThrowsIllegalArgumentExceptionIfNamePrefixIsEmpty(final @TempDir Path tempDir)
    {
        assertThrows(IllegalArgumentException.class, () -> rttHistogram.saveToFile(tempDir, ""));
    }

    @Test
    void saveToFileThrowsIllegalArgumentExceptionIfNamePrefixIsBlank(final @TempDir Path tempDir)
    {
        assertThrows(IllegalArgumentException.class, () -> rttHistogram.saveToFile(tempDir, " \t  \n  "));
    }

    @Test
    void saveToFileThrowsIOExceptionIfSaveFails(final @TempDir Path tempDir) throws IOException
    {
        final Path rootFile = Files.createFile(tempDir.resolve("my.txt"));

        final Histogram histogram = new Histogram(2);
        histogram.recordValue(2);
        histogram.recordValue(4);

        final RttHistogram rttHistogram = new RttHistogram(histogram.copy());

        assertThrows(IOException.class, () -> rttHistogram.saveToFile(rootFile, "ignore"));
    }

    @Test
    void saveToFileCreatesNewFileWithIndexZero(final @TempDir Path tempDir) throws IOException
    {
        Files.createFile(tempDir.resolve("another-one-13.hdr"));

        final Histogram histogram = new Histogram(3);
        histogram.setStartTimeStamp(123456789);
        histogram.setEndTimeStamp(987654321);
        histogram.recordValue(100);
        histogram.recordValue(1000);
        histogram.recordValue(250);

        final RttHistogram rttHistogram = new RttHistogram(histogram.copy());

        final Path file = rttHistogram.saveToFile(tempDir, "test-histogram");

        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertEquals("test-histogram-0.hdr", file.getFileName().toString());
        final Histogram savedHistogram = readHistogram(file);
        assertEquals(histogram, savedHistogram);
        assertEquals(histogram.getStartTimeStamp(), savedHistogram.getStartTimeStamp());
        assertEquals(histogram.getEndTimeStamp(), savedHistogram.getEndTimeStamp());
    }

    @Test
    void saveToFileCreatesNewFileByIncrementExistingMaxIndex(final @TempDir Path tempDir) throws IOException
    {
        Files.createFile(tempDir.resolve("another_one-13.hdr"));
        Files.createFile(tempDir.resolve("another_one" + AGGREGATE_FILE_SUFFIX));

        final Histogram histogram = new Histogram(2);
        histogram.recordValue(2);
        histogram.recordValue(4);

        final RttHistogram rttHistogram = new RttHistogram(histogram.copy());

        final Path file = rttHistogram.saveToFile(tempDir, "another_one");

        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertEquals("another_one-14.hdr", file.getFileName().toString());
        final Histogram savedHistogram = readHistogram(file);
        assertEquals(histogram, savedHistogram);
    }

    private Histogram readHistogram(final Path file) throws FileNotFoundException
    {
        final List<EncodableHistogram> histograms = new ArrayList<>();
        final HistogramLogReader logReader = new HistogramLogReader(file.toFile());
        try
        {
            while (logReader.hasNext())
            {
                histograms.add(logReader.nextIntervalHistogram());
            }
        }
        finally
        {
            logReader.close();
        }
        assertEquals(1, histograms.size());
        return (Histogram)histograms.get(0);
    }
}