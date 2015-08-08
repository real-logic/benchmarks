package uk.co.real_logic.benchmarks;

import org.HdrHistogram.DoubleHistogram;
import org.openjdk.jmh.annotations.*;

import java.util.Random;

@State(Scope.Thread)
public class MatrixBenchmark
{
    public static final int NUMBER_OF_SIGNIFICANT_VALUE_DIGITS = 3;
    public static final int RUNS = 200_000;
    public static final int PERIODS = 30 * 12;
    public static final int ARRAY_LENGTH = RUNS * PERIODS;
    public static final double MONTHLY_PAYMENT = 250.0;

    final double[] lumpSums = new double[PERIODS];
    final double[] riskFactors = new double[ARRAY_LENGTH];
    final double[] returnFactors = new double[ARRAY_LENGTH];
    final double[] totalReturns = new double[ARRAY_LENGTH];

    @Setup
    public void setup()
    {
        final  Random random = new Random();
        for (int i = 0; i < ARRAY_LENGTH; i++)
        {
            riskFactors[i] = random.nextDouble();
        }

        for (int i = 0; i < PERIODS; i++)
        {
            lumpSums[i] = MONTHLY_PAYMENT;
        }

        final double m = 0.7f;
        final double c = 0.25f;

        computeReturnFactors(m, c);
        computeTotalReturns();
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public double[] testReturnFactorsComputation()
    {
        final double m = 0.7f;
        final double c = 0.25f;

        return computeReturnFactors(m, c);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public double[] testSimulationComputation()
    {
        return computeTotalReturns();
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public DoubleHistogram[] testQuantilesComputationGood()
    {
        DoubleHistogram[] periodHistograms = new DoubleHistogram[PERIODS];
        int runIndex = 0;

        for (int p = 0; p < PERIODS; p++)
        {
            final DoubleHistogram histogram = new DoubleHistogram(3);
            periodHistograms[p] = histogram;

            for (int i = 0; i < RUNS; i++)
            {
                histogram.recordValue(totalReturns[runIndex]);
                runIndex++;
            }
        }

        return periodHistograms;
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public DoubleHistogram[] testQuantilesComputationBad()
    {
        DoubleHistogram[] periodHistograms = new DoubleHistogram[PERIODS];

        for (int p = 0; p < PERIODS; p++)
        {
            final DoubleHistogram histogram = new DoubleHistogram(NUMBER_OF_SIGNIFICANT_VALUE_DIGITS);
            periodHistograms[p] = histogram;

            int runIndex = p;
            for (int i = 0; i < RUNS; i++)
            {
                histogram.recordValue(totalReturns[runIndex]);
                runIndex += PERIODS;
            }
        }

        return periodHistograms;
    }

    private double[] computeReturnFactors(final double m, final double c)
    {
        for (int i = 0; i < ARRAY_LENGTH; i++)
        {
            returnFactors[i] = (riskFactors[i] * m) + c;
        }

        return returnFactors;
    }

    private double[] computeTotalReturns()
    {
        double totalReturn = 0.0;
        int runIndex = 0;

        for (int p = 0; p < PERIODS; p++)
        {
            final double lumpSum = lumpSums[p];

            for (int i = 0; i < RUNS; i++)
            {

                final int index = runIndex + i;
                totalReturn = (totalReturn + lumpSum) * returnFactors[index];
                totalReturns[index] = totalReturn;
            }

            runIndex += PERIODS;
        }

        return totalReturns;
    }
}
