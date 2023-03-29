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
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.ValueRecorder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.stream.Stream;

import static java.nio.file.Files.find;
import static java.nio.file.Files.isRegularFile;
import static org.agrona.AsciiEncoding.parseIntAscii;

public interface PersistedHistogram extends AutoCloseable
{
    /**
     * File extension used to persist histogram values on disc.
     */
    String FILE_EXTENSION = ".hdr";

    /**
     * File extension used to persist history of the histogram values on disc.
     */
    String HISTORY_FILE_EXTENSION = ".csv";

    /**
     * File suffix for aggregated histogram.
     */
    String AGGREGATE_FILE_SUFFIX = "-combined" + FILE_EXTENSION;

    /**
     * File name suffix for the report file, i.e. the plottable results.
     */
    String REPORT_FILE_SUFFIX = "-report.hgrm";

    /**
     * File name suffix for failed benchmark results.
     */
    String FAILED_FILE_SUFFIX = ".FAIL";

    /**
     * Separator character used to separate file name from the index.
     */
    char INDEX_SEPARATOR = '-';

    enum Status
    {
        OK,
        FAIL
    }

    /**
     * Produce textual representation of the value distribution of histogram data by percentile. The distribution is
     * output with exponentially increasing resolution, with each exponentially decreasing half-distance containing
     * five (5) percentile reporting tick points.
     *
     * @param printStream                 stream into which the distribution will be output.
     * @param outputValueUnitScalingRatio the scaling factor by which to divide histogram recorded values units in
     *                                    output.
     */
    void outputPercentileDistribution(PrintStream printStream, double outputValueUnitScalingRatio);

    /**
     * Save histogram into a file on disc. Uses provided {@code namePrefix} and appending an <em>index</em> to ensure
     * unique name, i.e. {@code namePrefix_index.hdr}. For example the first file with a given prefix
     * will be stored under index zero (e.g. {@code my-file_0.hdr}) and the fifth under index number four
     * (e.g. {@code my-file_4.hdr}).
     *
     * @param outputDirectory output directory where files should be stored.
     * @param namePrefix      name prefix to use when creating a file.
     * @param status          of the execution.
     * @return created file.
     * @throws NullPointerException     if {@code null == outputDirectory || null == namePrefix}.
     * @throws IllegalArgumentException if {@code namePrefix} is blank.
     * @throws IOException              if IO error occurs.
     */
    Path saveToFile(Path outputDirectory, String namePrefix, Status status) throws IOException;

    /**
     * Provide a value recorder to be used for measurements. Values recorded through this interface will be persisted
     * by this PersistedHistogram.
     *
     * @return the value recorder.
     */
    ValueRecorder valueRecorder();

    /**
     * Reset the histogram recording, generally between warmup and real runs.
     */
    void reset();

    /**
     * Returns an iterator over a sequence of histograms that form a recording history. Iterator may be empty or only
     * contain a single value depending on the data recorded and the underlying implementation. An implementation that
     * does not track history could return just a single value regardless of the amount of time spent during the
     * recording.
     *
     * @return a sequence of histograms in the form of an iterator.
     */
    Stream<Histogram> historyIterator();

    static Path saveHistogramToFile(
        final Histogram histogram, final Path outputDirectory, final String prefix, final Status status)
        throws IOException
    {
        final String fileNamePrefix = prefix + INDEX_SEPARATOR;
        final String fileExtension = FILE_EXTENSION;

        final int index = determineFileIndex(outputDirectory, fileNamePrefix, fileExtension);
        return saveToFile(histogram, outputDirectory.resolve(fileName(status, fileNamePrefix, fileExtension, index)));
    }

    static String fileName(
        final Status status, final String fileNamePrefix, final String fileExtension, final int index)
    {
        final String name = fileNamePrefix + index + fileExtension;
        if (Status.FAIL == status)
        {
            return name + FAILED_FILE_SUFFIX;
        }
        return name;
    }

    default Path saveHistoryToCsvFile(
        final Path outputDirectory, final String prefix, final Status status, final double... percentiles)
        throws IOException
    {
        final String fileNamePrefix = prefix + INDEX_SEPARATOR;
        final String fileExtension = HISTORY_FILE_EXTENSION;

        final int index = determineFileIndex(outputDirectory, fileNamePrefix, fileExtension);
        final Path csvPath = outputDirectory.resolve(fileName(status, fileNamePrefix, fileExtension, index));

        try (PrintStream output = new PrintStream(csvPath.toFile(), "ASCII"))
        {
            output.print("timestamp (ms)");
            for (final double percentile : percentiles)
            {
                output.print(",");
                output.print(percentile);
            }
            output.println();

            try (Stream<Histogram> history = historyIterator())
            {
                history.forEach(
                    (historyEntry) ->
                    {
                        final long midPointTimestamp = historyEntry.getStartTimeStamp() +
                            ((historyEntry.getEndTimeStamp() - historyEntry.getStartTimeStamp()) / 2);
                        output.print(midPointTimestamp);
                        for (final double percentile : percentiles)
                        {
                            output.print(",");
                            output.print(historyEntry.getValueAtPercentile(percentile));
                        }
                        output.println();
                    });
            }
        }

        return csvPath;
    }

    static int determineFileIndex(
        final Path outputDirectory,
        final String fileNamePrefix,
        final String fileExtension) throws IOException
    {
        try (
            Stream<Path> pathStream = find(
                outputDirectory,
                1,
                (file, attrs) ->
                {
                    if (!isRegularFile(file))
                    {
                        return false;
                    }

                    final String fileName = file.getFileName().toString();
                    return isHdrFile(fileName, fileExtension) &&
                        fileName.startsWith(fileNamePrefix);
                }))
        {
            return pathStream.mapToInt(
                (file) ->
                {
                    final String fileName = file.getFileName().toString();
                    final int failedSuffix = fileName.lastIndexOf(FAILED_FILE_SUFFIX);
                    final int lengthWithoutSuffix = failedSuffix > 0 ? failedSuffix : fileName.length();
                    final int indexStart = fileName.lastIndexOf(INDEX_SEPARATOR, lengthWithoutSuffix - 1) + 1;
                    return parseIntAscii(
                        fileName, indexStart, lengthWithoutSuffix - fileExtension.length() - indexStart);
                })
            .max()
            .orElse(-1) + 1;
        }
    }

    static boolean isHdrFile(final String fileName, final String fileExtension)
    {
        final int failedSuffix = fileName.lastIndexOf(FAILED_FILE_SUFFIX);
        final int lengthWithoutSuffix = failedSuffix > 0 ? failedSuffix : fileName.length();
        return fileName.startsWith(fileExtension, lengthWithoutSuffix - fileExtension.length()) &&
            !fileName.startsWith(
                AGGREGATE_FILE_SUFFIX, lengthWithoutSuffix - AGGREGATE_FILE_SUFFIX.length());
    }

    static Path saveToFile(final Histogram histogram, final Path file)
        throws FileNotFoundException
    {
        final HistogramLogWriter logWriter = new HistogramLogWriter(file.toFile());
        try
        {
            logWriter.outputIntervalHistogram(
                histogram.getStartTimeStamp() / 1000.0,
                histogram.getEndTimeStamp() / 1000.0,
                histogram,
                1.0);
        }
        finally
        {
            logWriter.close();
        }

        return file;
    }

    /**
     * {@inheritDoc}
     */
    void close();
}
