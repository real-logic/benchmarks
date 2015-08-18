package uk.co.real_logic.benchmarks;

import org.HdrHistogram.Histogram;
import org.openjdk.jmh.annotations.*;

import java.util.Random;

@State(Scope.Thread)
public class CombinedStepsMatrixBenchmark
{
    public static final int NUMBER_OF_SIGNIFICANT_VALUE_DIGITS = 2;
    public static final int RUNS = 200_000;
    public static final int PERIODS = 30 * 12;
    public static final int TOTAL_RUNS = RUNS * PERIODS;
    public static final int VECTOR_SIZE = TOTAL_RUNS + RUNS;
    public static final double MONTHLY_PAYMENT = 2500.0; // pennies rather than pounds

    final double[] lumpSums = new double[PERIODS];
    final double[] vector = new double[VECTOR_SIZE];
    final double[][] quantiles = new double[PERIODS][];
    final Histogram histogram = new Histogram(NUMBER_OF_SIGNIFICANT_VALUE_DIGITS);

    @Setup
    public void setup()
    {
        final Random random = new Random();
        for (int i = RUNS; i < VECTOR_SIZE; i++)
        {
            vector[i] = random.nextDouble();
        }

        for (int i = 0; i < PERIODS; i++)
        {
            quantiles[i] = new double[9];
            lumpSums[i] = MONTHLY_PAYMENT;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public double[][] testQuantilesComputationStandard()
    {
        final double m = 0.3;
        final double c = 0.25 + 1.0;

        int simulation = RUNS;
        for (int p = 0; p < PERIODS; p++)
        {
            final double lumpSum = lumpSums[p];

            for (int r = 0; r < RUNS; r++)
            {
                final int i = simulation + r;
                vector[i] = (m * vector[i]) + c; // compute return factors from risk factors
                vector[i] *= Math.exp((vector[i - RUNS] + lumpSum)); // compute accumulated returns
            }

            simulation += RUNS;
        }

        final Histogram histogram = this.histogram;
        simulation = RUNS;
        for (int p = 0; p < PERIODS; p++)
        {
            histogram.reset();

            for (int r = 0; r < RUNS; r++)
            {
                histogram.recordValue((long)vector[simulation++]);
            }

            quantiles[p][0] = histogram.getValueAtPercentile(1.0);
            quantiles[p][1] = histogram.getValueAtPercentile(5.0);
            quantiles[p][2] = histogram.getValueAtPercentile(10.0);
            quantiles[p][3] = histogram.getValueAtPercentile(25.0);
            quantiles[p][4] = histogram.getValueAtPercentile(50.0);
            quantiles[p][5] = histogram.getValueAtPercentile(75.0);
            quantiles[p][6] = histogram.getValueAtPercentile(90.0);
            quantiles[p][7] = histogram.getValueAtPercentile(95.0);
            quantiles[p][8] = histogram.getValueAtPercentile(99.0);
        }

        return quantiles;
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public double[][] testQuantilesComputationFast()
    {
        final double m = 0.3;
        final double c = 0.25 + 1.0;

        int simulation = RUNS;
        for (int p = 0; p < PERIODS; p++)
        {
            final double lumpSum = lumpSums[p];

            for (int r = 0; r < RUNS; r++)
            {
                final int i = simulation + r;
                vector[i] = (m * vector[i]) + c; // compute return factors from risk factors
                vector[i] *= exp((vector[i - RUNS] + lumpSum)); // compute accumulated returns
            }

            simulation += RUNS;
        }

        final Histogram histogram = this.histogram;
        simulation = RUNS;
        for (int p = 0; p < PERIODS; p++)
        {
            histogram.reset();

            for (int r = 0; r < RUNS; r++)
            {
                histogram.recordValue((long)vector[simulation++]);
            }

            quantiles[p][0] = histogram.getValueAtPercentile(1.0);
            quantiles[p][1] = histogram.getValueAtPercentile(5.0);
            quantiles[p][2] = histogram.getValueAtPercentile(10.0);
            quantiles[p][3] = histogram.getValueAtPercentile(25.0);
            quantiles[p][4] = histogram.getValueAtPercentile(50.0);
            quantiles[p][5] = histogram.getValueAtPercentile(75.0);
            quantiles[p][6] = histogram.getValueAtPercentile(90.0);
            quantiles[p][7] = histogram.getValueAtPercentile(95.0);
            quantiles[p][8] = histogram.getValueAtPercentile(99.0);
        }

        return quantiles;
    }

    /**
     * Fast approximation of {@link Math#exp(double)}.
     *
     * Returns Euler's number <i>e</i> raised to the power of a {@code double} value.
     *
     * <a href="http://martin.ankerl.com/2007/02/11/optimized-exponential-functions-for-java/">Blog entry</a>
     *
     * @param powValue for the power
     * @return Euler's number <i>e</i> raised to the power of a {@code double} value.
     */
    public static double exp(final double powValue)
    {
        final long tmp = (long)(1512775 * powValue + (1072693248 - 60801));

        return Double.longBitsToDouble(tmp << 32);
    }
}
