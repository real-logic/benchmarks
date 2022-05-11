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
package uk.co.real_logic.benchmarks.remote;

import org.HdrHistogram.*;
import org.agrona.CloseHelper;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * A persistent histogram that periodically logs a histogram of values. Primarily so that potential latency spikes
 * can be correlated over time.
 */
public class LoggingPersistedHistogram implements PersistedHistogram
{
    public static final String LOG_FILE_SUFFIX = ".hgrm";
    private final Path outputDirectory;
    private final SingleWriterRecorder recorder;
    private final long loggingIntervalMs = 1_000;

    private final BackgroundLogger backgroundLogger;
    private final EpochClock epochClock = SystemEpochClock.INSTANCE;
    private Histogram histogram = null;
    private File histogramLogFile = null;

    public LoggingPersistedHistogram(final Path outputDirectory, final SingleWriterRecorder recorder) throws IOException
    {
        this.outputDirectory = outputDirectory;
        this.recorder = recorder;
        this.backgroundLogger = new BackgroundLogger();
        this.backgroundLogger.start();
    }

    public void outputPercentileDistribution(final PrintStream printStream, final double outputValueUnitScalingRatio)
    {
        syncAndLoadHistogram();

        if (null == histogram)
        {
            throw new IllegalStateException("No histogram data recorded");
        }

        histogram.outputPercentileDistribution(printStream, outputValueUnitScalingRatio);
    }

    public Path saveToFile(final Path outputDirectory, final String namePrefix) throws IOException
    {
        syncAndLoadHistogram();

        requireNonNull(outputDirectory);

        final String prefix = namePrefix.trim();
        if (prefix.isEmpty())
        {
            throw new IllegalArgumentException("Name prefix cannot be blank!");
        }

        return PersistedHistogram.saveHistogramToFile(histogram, outputDirectory, prefix);
    }

    public ValueRecorder valueRecorder()
    {
        return recorder;
    }

    public void reset()
    {
        syncAndLoadHistogram();
        histogram = null;
        histogramLogFile = null;
        recorder.reset();
    }

    public Stream<Histogram> historyIterator()
    {
        syncAndLoadHistogram();

        try
        {
            final HistogramLogReader histogramLogReader = new HistogramLogReader(histogramLogFile);
            //noinspection Convert2Diamond
            final Iterator<Histogram> histogramIterator = new Iterator<Histogram>()
            {
                public boolean hasNext()
                {
                    final boolean hasNext = histogramLogReader.hasNext();

                    if (!hasNext)
                    {
                        histogramLogReader.close();
                    }

                    return hasNext;
                }

                public Histogram next()
                {
                    return (Histogram)histogramLogReader.nextIntervalHistogram();
                }
            };

            final Stream<Histogram> histogramStream = StreamSupport.stream(
                spliteratorUnknownSize(histogramIterator, Spliterator.ORDERED | Spliterator.NONNULL),
                false);
            histogramStream.onClose(histogramLogReader::close);
            return histogramStream;
        }
        catch (final IOException ex)
        {
            throw new IllegalStateException("Unable to produce history from log file");
        }
    }

    public void close()
    {
        try
        {
            backgroundLogger.stop();
        }
        catch (final Exception ex)
        {
            throw new IllegalStateException("Failed to stop background logger");
        }
    }

    private void syncAndLoadHistogram()
    {
        if (null == histogram)
        {
            try
            {
                histogramLogFile = backgroundLogger.sync();

                try (HistogramLogReader histogramLogReader = new HistogramLogReader(histogramLogFile))
                {
                    final Histogram aggregateHistogram = new Histogram(3);
                    while (histogramLogReader.hasNext())
                    {
                        final Histogram loadedHistogram = (Histogram)histogramLogReader.nextIntervalHistogram();
                        aggregateHistogram.add(loadedHistogram);
                    }

                    histogram = aggregateHistogram;
                }
            }
            catch (final Exception ex)
            {
                throw new IllegalStateException("Unable to sync with background histogram recording");
            }
        }
    }

    private class BackgroundLogger
    {
        private final Thread backgroundLoggerThread;
        private HistogramLogWriter histogramLogWriter;
        private File currentLogFile;
        private File previousLogFile;
        private long lastLogTimestampMs;
        private Histogram sampleHistogram = null;

        private volatile CountDownLatch syncLatch = null;

        BackgroundLogger() throws IOException
        {
            this.currentLogFile = File.createTempFile("LoggingHistogram", ".hgrm", outputDirectory.toFile());
            this.histogramLogWriter = new HistogramLogWriter(currentLogFile);
            this.backgroundLoggerThread = new Thread(this::run);
            backgroundLoggerThread.setName("LoggingPersistedHistogram.BackgroundLogger");
            backgroundLoggerThread.setDaemon(true);
        }

        void poll()
        {
            final long time = epochClock.time();
            final boolean applySyncLatch = null != syncLatch;
            if (!applySyncLatch && time < (lastLogTimestampMs + loggingIntervalMs))
            {
                return;
            }

            sampleHistogram = recorder.getIntervalHistogram(sampleHistogram);
            histogramLogWriter.outputIntervalHistogram(sampleHistogram);
            lastLogTimestampMs = sampleHistogram.getEndTimeStamp();

            if (applySyncLatch)
            {
                histogramLogWriter.close();
                previousLogFile = currentLogFile;
                try
                {
                    currentLogFile = File.createTempFile("LoggingHistogram", LOG_FILE_SUFFIX, outputDirectory.toFile());
                    histogramLogWriter = new HistogramLogWriter(currentLogFile);
                }
                catch (final IOException ex)
                {
                    throw new RuntimeException(ex);
                }
                finally
                {
                    syncLatch.countDown();
                    syncLatch = null;
                }
            }
        }

        @SuppressWarnings("BusyWait")
        private void run()
        {
            while (!Thread.currentThread().isInterrupted())
            {
                poll();
                try
                {
                    Thread.sleep(100);
                }
                catch (final InterruptedException ex)
                {
                    break;
                }
            }
        }

        private File sync() throws InterruptedException
        {
            syncLatch = new CountDownLatch(1);
            syncLatch.await();
            return previousLogFile;
        }

        private void stop() throws InterruptedException, IOException
        {
            backgroundLoggerThread.interrupt();
            backgroundLoggerThread.join();
            CloseHelper.close(() -> histogramLogWriter.close());
            try (Stream<Path> logFiles =
                Files.list(outputDirectory).filter(p -> p.toString().endsWith(LOG_FILE_SUFFIX)))
            {
                for (final Iterator<Path> logFilesIterator = logFiles.iterator(); logFilesIterator.hasNext();)
                {
                    Files.delete(logFilesIterator.next());
                }
            }
        }

        public void start()
        {
            backgroundLoggerThread.start();
        }
    }
}
