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
package uk.co.real_logic.benchmarks.rtt;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.stream.Stream;

import static java.nio.file.Files.find;
import static java.nio.file.Files.isRegularFile;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.agrona.AsciiEncoding.parseIntAscii;

final class RttHistogram
{
    static final String FILE_EXTENSION = ".hdr";
    static final String AGGREGATE_FILE_SUFFIX = "-combined" + FILE_EXTENSION;

    private final Histogram histogram;

    RttHistogram()
    {
        this(new Histogram(HOURS.toNanos(1), 3));
    }

    RttHistogram(final Histogram histogram)
    {
        this.histogram = histogram;
    }

    /**
     * Discard recorder values.
     */
    public void reset()
    {
        histogram.reset();
    }

    /**
     * Record a value in the histogram.
     *
     * @param value RTT value to be recorded.
     * @throws ArrayIndexOutOfBoundsException (may throw) if value is exceeds highestTrackableValue
     */
    public void recordValue(final long value)
    {
        histogram.recordValue(value);
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
    public void outputPercentileDistribution(final PrintStream printStream, final double outputValueUnitScalingRatio)
    {
        histogram.outputPercentileDistribution(printStream, outputValueUnitScalingRatio);
    }

    /**
     * Save histogram into a file on disc. Uses provided {@code namePrefix} and appending an <em>index</em> to ensure
     * unique name, i.e. {@code namePrefix_index.hdr}. For example the first file with a given prefix
     * will be stored under index zero (e.g. {@code my-file_0.hdr}) and the fifth under index number four
     * (e.g. {@code my-file_4.hdr}).
     *
     * @param outputDirectory output directory where files should be stored.
     * @param namePrefix      name prefix to use when creating a file.
     * @return created file.
     * @throws NullPointerException     if {@code null == outputDirectory || null == namePrefix}.
     * @throws IllegalArgumentException if {@code namePrefix} is blank.
     * @throws IOException              if IO error occurs.
     */
    public Path saveToFile(final Path outputDirectory, final String namePrefix) throws IOException
    {
        requireNonNull(outputDirectory);

        final String prefix = namePrefix.trim();
        if (prefix.isEmpty())
        {
            throw new IllegalArgumentException("Name prefix cannot be blank!");
        }

        final String fileNamePrefix = prefix + "-";
        final Stream<Path> pathStream = find(
            outputDirectory,
            1,
            (file, attrs) ->
            {
                if (!isRegularFile(file))
                {
                    return false;
                }

                final String fileName = file.getFileName().toString();

                return fileName.endsWith(FILE_EXTENSION) &&
                    !fileName.endsWith(AGGREGATE_FILE_SUFFIX) &&
                    fileName.startsWith(fileNamePrefix);
            });

        final int index = pathStream.mapToInt(
            (file) ->
            {
                final String fileName = file.getFileName().toString();
                final int indexLength = fileName.length() - FILE_EXTENSION.length() - fileNamePrefix.length();
                return parseIntAscii(fileName, fileNamePrefix.length(), indexLength);
            })
            .max()
            .orElse(-1) + 1;

        return saveToFile(histogram, 1.0, outputDirectory.resolve(fileNamePrefix + index + FILE_EXTENSION));
    }

    static Path saveToFile(final Histogram histogram, final double maxValueUnitRatio, final Path file)
        throws FileNotFoundException
    {
        final HistogramLogWriter logWriter = new HistogramLogWriter(file.toFile());
        try
        {
            logWriter.outputIntervalHistogram(
                histogram.getStartTimeStamp() / 1000.0,
                histogram.getEndTimeStamp() / 1000.0,
                histogram,
                maxValueUnitRatio);
        }
        finally
        {
            logWriter.close();
        }

        return file;
    }
}
