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

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.benchmarks.remote.PersistedHistogram.Status.OK;
import static uk.co.real_logic.benchmarks.remote.PersistedHistogramTest.readHistogram;

class SinglePersistedHistogramTest
{
    private final Histogram histogram = mock(Histogram.class);
    private final SinglePersistedHistogram singlePersistedHistogram = new SinglePersistedHistogram(histogram);

    @Test
    void outputPercentileDistribution()
    {
        final PrintStream out = System.out;
        final double scaleRatio = -2.5;

        singlePersistedHistogram.outputPercentileDistribution(out, scaleRatio);

        verify(histogram).outputPercentileDistribution(out, scaleRatio);
        verifyNoMoreInteractions(histogram);
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

        final SinglePersistedHistogram singlePersistedHistogram = new SinglePersistedHistogram(histogram.copy());

        final Path file = singlePersistedHistogram.saveToFile(tempDir, "test-histogram", OK);

        assertNotNull(file);
        assertTrue(Files.exists(file));
        assertEquals("test-histogram_status=OK_0.hdr", file.getFileName().toString());
        final Histogram savedHistogram = readHistogram(file);
        assertEquals(histogram, savedHistogram);
        assertEquals(histogram.getStartTimeStamp(), savedHistogram.getStartTimeStamp());
        assertEquals(histogram.getEndTimeStamp(), savedHistogram.getEndTimeStamp());
    }
}
