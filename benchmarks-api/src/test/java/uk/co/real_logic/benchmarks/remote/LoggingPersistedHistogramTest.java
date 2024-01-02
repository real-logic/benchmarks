/*
 * Copyright 2015-2024 Real Logic Limited.
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
import org.HdrHistogram.SingleWriterRecorder;
import org.HdrHistogram.ValueRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.co.real_logic.benchmarks.remote.PersistedHistogram.AGGREGATE_FILE_SUFFIX;
import static uk.co.real_logic.benchmarks.remote.PersistedHistogram.Status.OK;

class LoggingPersistedHistogramTest
{
    @Test
    void shouldRecordHistory(final @TempDir Path tempDir) throws IOException, InterruptedException
    {
        final long[] counts = { 0, 0 };
        final int totalCountIndex = 0;
        final int entryCountIndex = 1;
        final Path results;

        try (PersistedHistogram histogram = new LoggingPersistedHistogram(tempDir, new SingleWriterRecorder(3)))
        {
            Files.createFile(tempDir.resolve("another_one" + AGGREGATE_FILE_SUFFIX));

            final Histogram expectedHistogram = new Histogram(3);
            final ValueRecorder valueRecorder = histogram.valueRecorder();
            final Random r = new Random();

            histogram.reset();

            for (int i = 0; i < 1000; i++)
            {
                for (int j = 0; j < 5; j++)
                {
                    final int value = r.nextInt(1000);
                    valueRecorder.recordValue(value);
                    expectedHistogram.recordValue(value);
                    Thread.sleep(1);
                }
            }

            histogram.saveToFile(tempDir, "results", OK);
            try (Stream<Histogram> history = histogram.historyIterator())
            {
                history.forEach(
                    (h) ->
                    {
                        counts[totalCountIndex] += h.getTotalCount();
                        counts[entryCountIndex]++;
                    });
            }

            assertEquals(5000, counts[totalCountIndex]);
            // Number of files is not deterministic and varies with the speed of the system.
            assertTrue(5 <= counts[entryCountIndex]);

            results = histogram.saveHistoryToCsvFile(tempDir, "results", OK, 50.0, 99.0, 99.99, 100.0);
        }
        final List<String> strings = Files.readAllLines(results);
        assertEquals(counts[entryCountIndex] + 1, strings.size());
    }
}
