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
import org.HdrHistogram.HistogramLogReader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static java.nio.file.Files.*;
import static java.util.stream.Collectors.groupingBy;
import static uk.co.real_logic.benchmarks.rtt.RttHistogram.*;
import static uk.co.real_logic.benchmarks.rtt.RttHistogram.AGGREGATE_FILE_SUFFIX;
import static uk.co.real_logic.benchmarks.rtt.RttHistogram.FILE_EXTENSION;

public final class ResultsAggregator
{
    private final Path directory;
    private final double outputValueUnitScalingRatio;

    public ResultsAggregator(final Path directory, final double outputValueUnitScalingRatio)
    {
        if (!exists(directory))
        {
            throw new IllegalArgumentException("directory does not exist: " + directory.toAbsolutePath());
        }

        if (!isDirectory(directory))
        {
            throw new IllegalArgumentException(directory.toAbsolutePath() + " is not a directory!");
        }

        if (Double.isNaN(outputValueUnitScalingRatio) || Double.compare(outputValueUnitScalingRatio, 0.0) <= 0)
        {
            throw new IllegalArgumentException(
                "output value scale ratio must a positive number, got: " + outputValueUnitScalingRatio);
        }

        this.directory = directory;
        this.outputValueUnitScalingRatio = outputValueUnitScalingRatio;
    }

    public void run() throws IOException
    {
        final Map<String, List<Path>> byPrefix = walk(directory)
            .filter((path) ->
            {
                if (!isRegularFile(path))
                {
                    return false;
                }

                final String fileName = path.getFileName().toString();
                return fileName.endsWith(FILE_EXTENSION) && !fileName.endsWith(AGGREGATE_FILE_SUFFIX);
            })
            .collect(groupingBy((file) ->
            {
                final String fileName = file.getFileName().toString();
                return fileName.substring(0, fileName.lastIndexOf('-'));
            }));

        for (final Entry<String, List<Path>> e : byPrefix.entrySet())
        {
            final Histogram aggregate = aggregateHistograms(e);
            saveToFile(aggregate, outputValueUnitScalingRatio, directory.resolve(e.getKey() + AGGREGATE_FILE_SUFFIX));
        }
    }

    private Histogram aggregateHistograms(final Entry<String, List<Path>> entry) throws FileNotFoundException
    {
        Histogram aggregate = null;

        for (final Path file : entry.getValue())
        {
            try (HistogramLogReader logReader = new HistogramLogReader(file.toFile()))
            {
                while (logReader.hasNext())
                {
                    final Histogram histogram = (Histogram)logReader.nextIntervalHistogram();
                    if (null == aggregate)
                    {
                        aggregate = histogram;
                    }
                    else
                    {
                        aggregate.add(histogram);
                    }
                }
            }
        }

        return aggregate;
    }

    public static void main(final String[] args) throws IOException
    {
        if (args.length < 1 || args.length > 2)
        {
            printHelp();
            System.exit(-1);
        }

        final Path directory = Paths.get(args[0]);
        final ResultsAggregator resultsAggregator = new ResultsAggregator(
            directory, args.length == 2 ? Double.parseDouble(args[1]) : 1.0);

        resultsAggregator.run();
    }

    private static void printHelp()
    {
        System.out.println("Usage: <input-dir> [outputValueUnitScalingRatio] - aggregates multiple histogram files");
        System.out.println("  from the `input-dir` into a single file grouping them by a common prefix.");
        System.out.println("  For example if the `input-dir` contains files `my-0.hdr`, `my-6.hdr` and `other-3.hdr`");
        System.out.println("  the result of executing the aggregator will be two new files in the same directory,");
        System.out.println("  i.e. `my-combined.hdr` (combination of the `my-0.hdr` and `my-6.hdr`) and");
        System.out.println("  `other-combined.hdr` (contains the `other-0.hdr` histogram).");
        System.out.println();
        System.out.println("  Input arguments:");
        System.out.println("  `input-dir` - is the directory containing results files to be aggregated");
        System.out.println("  `outputValueUnitScalingRatio` - is the scaling factor by which to divide histogram");
        System.out.println("  recorded values units in output.");
        System.out.println("  Default value is 1.0, i.e. the output will be in nanoseconds.");
        System.out.println("  For example setting `outputValueUnitScalingRatio` to 1000.0 will change the output");
        System.out.println("  to be in microseconds.");
    }
}
