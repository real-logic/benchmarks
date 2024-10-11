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

import org.HdrHistogram.ValueRecorder;
import org.agrona.AsciiNumberFormatException;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.System.setProperty;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static uk.co.real_logic.benchmarks.remote.Configuration.*;

class ConfigurationTest
{
    @BeforeEach
    void before()
    {
        clearConfigProperties();
    }

    @AfterEach
    void after()
    {
        clearConfigProperties();
    }

    @Test
    void throwsIllegalArgumentExceptionIfWarmUpIterationsIsANegativeNumber()
    {
        final Builder builder = new Builder()
            .warmupIterations(-1);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("'" + WARMUP_ITERATIONS_PROP_NAME + "' cannot be less than 0, got: -1", ex.getMessage());
    }

    @Test
    void warmupIterationsCanBeZero()
    {
        final Configuration configuration = new Builder()
            .warmupIterations(0)
            .messageRate(1_000)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputFileNamePrefix("test")
            .build();

        assertEquals(0, configuration.warmupIterations());
    }

    @Test
    void warmupMessageRateIterationsCanBeZero()
    {
        final Configuration configuration = new Builder()
            .warmupMessageRate(0)
            .messageRate(1_000)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputFileNamePrefix("test")
            .build();

        assertEquals(0, configuration.warmupMessageRate());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void trackHistoryCanBeSet(final boolean trackHistory)
    {
        final Configuration configuration = new Builder()
            .warmupMessageRate(1)
            .messageRate(1_000)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .trackHistory(trackHistory)
            .outputFileNamePrefix("test")
            .build();

        assertEquals(trackHistory, configuration.trackHistory());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void reportProgressCanBeSet(final boolean reportProgress)
    {
        final Configuration configuration = new Builder()
            .warmupMessageRate(1)
            .messageRate(1_000)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .reportProgress(reportProgress)
            .outputFileNamePrefix("test")
            .build();

        assertEquals(reportProgress, configuration.reportProgress());
    }

    @ParameterizedTest
    @ValueSource(ints = { -200, 0 })
    void throwsIllegalArgumentExceptionIfIterationsIsInvalid(final int iterations)
    {
        final Builder builder = new Builder()
            .iterations(iterations);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("'" + ITERATIONS_PROP_NAME + "' cannot be less than 1, got: " + iterations,
            ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { -123, 0 })
    void throwsIllegalArgumentExceptionIfMessageRateIsLessThanOne(final int messageRate)
    {
        final Builder builder = new Builder()
            .messageRate(messageRate);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("'" + MESSAGE_RATE_PROP_NAME + "' cannot be less than 1, got: " + messageRate, ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1_000_000_001, Integer.MAX_VALUE })
    void throwsIllegalArgumentExceptionIfMessageRateExceedsMaxValue(final int messageRate)
    {
        final Builder builder = new Builder()
            .messageRate(messageRate);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals(
            "'" + MESSAGE_RATE_PROP_NAME + "' cannot be greater than " + MAX_MESSAGE_RATE + ", got: " + messageRate,
            ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1_000_000_001, Integer.MAX_VALUE })
    void throwsIllegalArgumentExceptionIfWarmupMessageRateExceedsMaxValue(final int messageRate)
    {
        final Builder builder = new Builder()
            .warmupMessageRate(messageRate);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals(
            "'" + WARMUP_MESSAGE_RATE_PROP_NAME + "' cannot be greater than " + MAX_MESSAGE_RATE + ", got: " +
            messageRate,
            ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, 0 })
    void throwsIllegalArgumentExceptionIfBatchSizeIsInvalid(final int size)
    {
        final Builder builder = new Builder()
            .messageRate(1000)
            .batchSize(size);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("'" + BATCH_SIZE_PROP_NAME + "' cannot be less than 1, got: " + size, ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource("messageSizes")
    void throwsIllegalArgumentExceptionIfMessageLengthIsLessThanMinimumSize(final int length)
    {
        final Builder builder = new Builder()
            .messageRate(200)
            .messageLength(length);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals(
            "'" + MESSAGE_LENGTH_PROP_NAME + "' cannot be less than " + MIN_MESSAGE_LENGTH + ", got: " + length,
            ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfMessageTransceiverClassIsNull()
    {
        final Builder builder = new Builder()
            .messageRate(10);

        final NullPointerException ex = assertThrows(NullPointerException.class, builder::build);

        assertEquals("MessageTransceiver class cannot be null", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessageTransceiverClassIsAnAbstractClass()
    {
        final Builder builder = new Builder()
            .messageRate(10)
            .messageTransceiverClass(MessageTransceiver.class);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("MessageTransceiver class must be a concrete class", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessageTransceiverClassHasNoPublicConstructor()
    {
        final Builder builder = new Builder()
            .messageRate(10)
            .messageTransceiverClass(TestNoPublicConstructorMessageTransceiver.class);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals(
            "MessageTransceiver class must have a public constructor that takes a NanoClock and a ValueRecorder",
            ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfIdleStrategyIsNull()
    {
        final Builder builder = new Builder()
            .messageRate(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .idleStrategy(null);

        final NullPointerException ex = assertThrows(NullPointerException.class, builder::build);

        assertEquals("'" + IDLE_STRATEGY_PROP_NAME + "' cannot be null", ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfOutputDirectoryIsNull()
    {
        final Builder builder = new Builder()
            .messageRate(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(null);

        final NullPointerException ex = assertThrows(NullPointerException.class, builder::build);

        assertEquals("output directory cannot be null", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfOutputDirectoryIsNotADirectory(final @TempDir Path tempDir) throws IOException
    {
        final Path outputDirectory = Files.createTempFile(tempDir, "test", "file");

        final Builder builder = new Builder()
            .messageRate(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(outputDirectory);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("output path is not a directory: " + outputDirectory, ex.getMessage());
    }

    @Test
    @EnabledOnOs({ OS.LINUX, OS.MAC })
    void throwsIllegalArgumentExceptionIfOutputDirectoryIsNotWriteable(final @TempDir Path tempDir) throws IOException
    {
        final Path outputDirectory = Files.createDirectory(tempDir.resolve("read-only"),
            PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ)));

        final Builder builder = new Builder()
            .messageRate(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(outputDirectory);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("output directory is not writeable: " + outputDirectory, ex.getMessage());
    }

    @Test
    @EnabledOnOs({ OS.LINUX, OS.MAC })
    void throwsIllegalArgumentExceptionIfOutputDirectoryCannotBeCreated(final @TempDir Path tempDir) throws IOException
    {
        final Path rootDirectory = Files.createDirectory(tempDir.resolve("read-only"),
            PosixFilePermissions.asFileAttribute(EnumSet.of(PosixFilePermission.OWNER_READ)));
        final Path outputDirectory = rootDirectory.resolve("actual-dir");

        final Builder builder = new Builder()
            .messageRate(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(outputDirectory);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("failed to create output directory: " + outputDirectory, ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfOutputFileNamePrefixIsEmpty(final @TempDir Path tempDir)
    {
        final Builder builder = new Builder()
            .messageRate(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(tempDir)
            .outputFileNamePrefix(" \t");

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("Output file name prefix cannot be empty!", ex.getMessage());
    }

    @Test
    void outputFileNamePrefixAddsHashValueComputedFromSystemProperties(final @TempDir Path tempDir)
    {
        final Configuration configuration = new Builder()
            .messageRate(12)
            .batchSize(3)
            .messageLength(75)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("the-prefix")
            .systemProperties(props("E", "m*c^2"))
            .build();

        assertEquals(
            "the-prefix_rate=12_batch=3_length=75" +
            "_sha=a2bea3034417edbbe21e66dd9b68d43fe53e287e04a1f6b119741ab9e0729f60",
            configuration.outputFileNamePrefix());
    }

    @ParameterizedTest
    @CsvSource({
        "1000000000,1000M",
        "5000,5K",
        "12345,12345",
        "7890000,7890K",
        "400000,400K",
        "5430,5430",
        "100,100",
        "7600,7600",
        "10,10" })
    void outputFileNamePrefixUsesHumanReadableRateValues(
        final String rate, final String expectedOutput, final @TempDir Path tempDir)
    {
        final Configuration configuration = new Builder()
            .messageRate(Integer.parseInt(rate))
            .batchSize(3)
            .messageLength(75)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("the-prefix")
            .systemProperties(props("E", "m*c^2"))
            .build();

        final String outputFileNamePrefix = configuration.outputFileNamePrefix();
        final int startIndex = outputFileNamePrefix.indexOf("rate=");
        final int endIndex = outputFileNamePrefix.indexOf('_', startIndex + 1);
        assertEquals("rate=" + expectedOutput, outputFileNamePrefix.substring(startIndex, endIndex));
    }

    @Test
    void defaultOptions()
    {
        final Configuration configuration = new Builder()
            .messageRate(123)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputFileNamePrefix("defaults")
            .systemProperties(new Properties())
            .build();

        assertEquals(123, configuration.messageRate());
        assertEquals(DEFAULT_WARMUP_ITERATIONS, configuration.warmupIterations());
        assertEquals(DEFAULT_WARMUP_MESSAGE_RATE, configuration.warmupMessageRate());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BATCH_SIZE, configuration.batchSize());
        assertEquals(MIN_MESSAGE_LENGTH, configuration.messageLength());
        assertSame(InMemoryMessageTransceiver.class, configuration.messageTransceiverClass());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.idleStrategy());
        assertEquals(Paths.get("results").toAbsolutePath(), configuration.outputDirectory());
        assertEquals("defaults_rate=123_batch=" + DEFAULT_BATCH_SIZE + "_length=" + MIN_MESSAGE_LENGTH +
            "_sha=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            configuration.outputFileNamePrefix());
    }

    @Test
    void explicitOptions(final @TempDir Path tempDir)
    {
        final Path outputDirectory = tempDir.resolve("my-output-dir");
        final Configuration configuration = new Builder()
            .warmupIterations(3)
            .warmupMessageRate(444)
            .iterations(11)
            .messageRate(666)
            .batchSize(4)
            .messageLength(119)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .idleStrategy(YieldingIdleStrategy.INSTANCE)
            .outputDirectory(outputDirectory)
            .outputFileNamePrefix("explicit-opts")
            .build();

        assertEquals(3, configuration.warmupIterations());
        assertEquals(444, configuration.warmupMessageRate());
        assertEquals(11, configuration.iterations());
        assertEquals(666, configuration.messageRate());
        assertEquals(4, configuration.batchSize());
        assertEquals(119, configuration.messageLength());
        assertSame(InMemoryMessageTransceiver.class, configuration.messageTransceiverClass());
        assertSame(YieldingIdleStrategy.INSTANCE, configuration.idleStrategy());
        assertEquals(outputDirectory.toAbsolutePath(), configuration.outputDirectory());
        assertTrue(configuration.outputFileNamePrefix().startsWith("explicit-opts"));
    }

    @Test
    void toStringPrintsConfiguredValues()
    {
        final Configuration configuration = new Builder()
            .warmupIterations(4)
            .warmupMessageRate(999)
            .iterations(10)
            .messageRate(777000)
            .batchSize(2)
            .messageLength(64)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .idleStrategy(NoOpIdleStrategy.INSTANCE)
            .outputFileNamePrefix("my-file")
            .systemProperties(props("java", "25"))
            .build();

        assertEquals("Configuration{" +
            "\n    warmUpIterations=4" +
            "\n    warmupMessageRate=999" +
            "\n    iterations=10" +
            "\n    messageRate=777K" +
            "\n    batchSize=2" +
            "\n    messageLength=64" +
            "\n    messageTransceiverClass=uk.co.real_logic.benchmarks.remote.InMemoryMessageTransceiver" +
            "\n    idleStrategy=NoOpIdleStrategy{alias=noop}" +
            "\n    trackHistory=false" +
            "\n    reportProgress=true" +
            "\n    outputDirectory=" + Paths.get("results").toAbsolutePath() +
            "\n    outputFileNamePrefix=my-file_rate=777K_batch=2_length=64" +
            "_sha=73ccec448ba12264acb12e7f9f36fddc73e8c62e43549b786a901c88891610c9" +
            "\n}",
            configuration.toString());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesIsNotConfigured()
    {
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("property '" + MESSAGE_RATE_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesHasInvalidValue()
    {
        setProperty(MESSAGE_RATE_PROP_NAME, "100x000");

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("invalid rate specified in '" + MESSAGE_RATE_PROP_NAME + "'", ex.getMessage());
        final Throwable cause = ex.getCause();
        assertInstanceOf(AsciiNumberFormatException.class, cause);
        assertEquals("error parsing int: 100x000", cause.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesHasInvalidValueWithSuffix()
    {
        setProperty(MESSAGE_RATE_PROP_NAME, "25i");

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("invalid rate specified in '" + MESSAGE_RATE_PROP_NAME + "'", ex.getMessage());
        final Throwable cause = ex.getCause();
        assertInstanceOf(NumberFormatException.class, cause);
        assertEquals(MESSAGE_RATE_PROP_NAME + ": 25i should end with: K or M.", cause.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "uk.co.real_logic.benchmarks.remote.message.rate, 3000M",
        "uk.co.real_logic.benchmarks.remote.warmup.message.rate, 2456789K" })
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfRateOverflows(final String prop, final String value)
    {
        setProperty(MESSAGE_RATE_PROP_NAME, "1");
        setProperty(prop, value);

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("invalid rate specified in '" + prop + "'", ex.getMessage());
        final Throwable cause = ex.getCause();
        assertInstanceOf(NumberFormatException.class, cause);
        assertEquals(prop + " would overflow an int: " + value, cause.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfMessageTransceiverPropertyIsNotConfigured()
    {
        setProperty(MESSAGE_RATE_PROP_NAME, "100");

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("property '" + MESSAGE_TRANSCEIVER_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfMessageTransceiverHasInvalidValue()
    {
        setProperty(MESSAGE_RATE_PROP_NAME, "20M");
        setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, Integer.class.getName());

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("invalid class value for property '" + MESSAGE_TRANSCEIVER_PROP_NAME +
            "', cause: class java.lang.Integer", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesDefaults()
    {
        setProperty(OUTPUT_FILE_NAME_PROP_NAME, "test-out-prefix");
        setProperty(MESSAGE_RATE_PROP_NAME, "42K");
        setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, InMemoryMessageTransceiver.class.getName());

        final Configuration configuration = fromSystemProperties();

        assertEquals(42_000, configuration.messageRate());
        assertEquals(DEFAULT_WARMUP_ITERATIONS, configuration.warmupIterations());
        assertEquals(DEFAULT_WARMUP_MESSAGE_RATE, configuration.warmupMessageRate());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BATCH_SIZE, configuration.batchSize());
        assertEquals(MIN_MESSAGE_LENGTH, configuration.messageLength());
        assertSame(InMemoryMessageTransceiver.class, configuration.messageTransceiverClass());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.idleStrategy());
        assertEquals(Paths.get("results").toAbsolutePath(), configuration.outputDirectory());
    }

    @Test
    void fromSystemPropertiesOverrideAll(final @TempDir Path tempDir)
    {
        setProperty(WARMUP_ITERATIONS_PROP_NAME, "2");
        setProperty(WARMUP_MESSAGE_RATE_PROP_NAME, "78K");
        setProperty(ITERATIONS_PROP_NAME, "4");
        setProperty(MESSAGE_RATE_PROP_NAME, "1000M");
        setProperty(BATCH_SIZE_PROP_NAME, "3");
        setProperty(MESSAGE_LENGTH_PROP_NAME, "24");
        setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, InMemoryMessageTransceiver.class.getName());
        setProperty(IDLE_STRATEGY_PROP_NAME, YieldingIdleStrategy.class.getName());
        final Path outputDirectory = tempDir.resolve("my-output-dir-prop");
        setProperty(OUTPUT_DIRECTORY_PROP_NAME, outputDirectory.toAbsolutePath().toString());
        setProperty(OUTPUT_FILE_NAME_PROP_NAME, "my-out-file");
        setProperty(TRACK_HISTORY_PROP_NAME, "true");
        setProperty(REPORT_PROGRESS_PROP_NAME, "false");

        final Configuration configuration = fromSystemProperties();

        assertEquals(2, configuration.warmupIterations());
        assertEquals(78_000, configuration.warmupMessageRate());
        assertEquals(4, configuration.iterations());
        assertEquals(MAX_MESSAGE_RATE, configuration.messageRate());
        assertEquals(3, configuration.batchSize());
        assertEquals(24, configuration.messageLength());
        assertSame(InMemoryMessageTransceiver.class, configuration.messageTransceiverClass());
        assertInstanceOf(YieldingIdleStrategy.class, configuration.idleStrategy());
        assertTrue(configuration.trackHistory());
        assertFalse(configuration.reportProgress());
        assertEquals(outputDirectory.toAbsolutePath(), configuration.outputDirectory());
        assertTrue(configuration.outputFileNamePrefix().startsWith("my-out-file"));
    }

    @ParameterizedTest
    @MethodSource("computeSha256Inputs")
    void computeSha256FromProperties(final Properties properties, final String sha256)
    {
        assertEquals(sha256, computeSha256(properties));
    }

    static List<Arguments> computeSha256Inputs()
    {
        return asList(
            arguments(new Properties(),
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            arguments(props("emptyKey", ""),
                "7b0cfdb635b9790840cbe4d9caca23a03240f1d13da3dccd92255e4208127f08"),
            arguments(props("java", "¯\\_(ツ)_/¯"),
                "750e8c40c5473ea1d8eae9c27b1fe61b6e8249f87db30fca5ececc57cba14afe"),
            arguments(props("java", "\uD83E\uDD37"),
                "75d681403cdcc3fd6ada5cdb383e18c7af2862b750ddc670895471cae30bf76b"),
            arguments(props("X", "-100", "B", "2", "z", "0", "\uD83E\uDD37", "42", "y", "2.25"),
                "8bc055dc860587df8a9234d6721e6a482dd707e204f29895eee08aeeaaaf4432"),
            arguments(props(
                "\uD83E\uDD37", "42",
                "B", "2",
                "X", "-100",
                "y", "2.25",
                "z", "0",
                OUTPUT_FILE_NAME_PROP_NAME, "ignore me",
                OUTPUT_DIRECTORY_PROP_NAME, "and me too"),
                "8bc055dc860587df8a9234d6721e6a482dd707e204f29895eee08aeeaaaf4432"));
    }

    private static Properties props(final String... keyValuePairs)
    {
        assertEquals(0, keyValuePairs.length & 1);
        final Properties properties = new Properties();
        for (int i = 0; i < keyValuePairs.length; i += 2)
        {
            properties.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return properties;
    }

    private void clearConfigProperties()
    {
        Stream.of(
            WARMUP_ITERATIONS_PROP_NAME,
            WARMUP_MESSAGE_RATE_PROP_NAME,
            ITERATIONS_PROP_NAME,
            MESSAGE_RATE_PROP_NAME,
            BATCH_SIZE_PROP_NAME,
            MESSAGE_LENGTH_PROP_NAME,
            MESSAGE_TRANSCEIVER_PROP_NAME,
            IDLE_STRATEGY_PROP_NAME,
            OUTPUT_DIRECTORY_PROP_NAME,
            OUTPUT_FILE_NAME_PROP_NAME)
            .forEach(System::clearProperty);
    }

    private static IntStream messageSizes()
    {
        return IntStream.range(-1, MIN_MESSAGE_LENGTH);
    }

    public static final class TestNoPublicConstructorMessageTransceiver extends MessageTransceiver
    {
        private TestNoPublicConstructorMessageTransceiver(final NanoClock clock, final ValueRecorder valueRecorder)
        {
            super(clock, valueRecorder);
        }

        public void init(final Configuration configuration)
        {
        }

        public void destroy()
        {
        }

        public int send(final int messageRate, final int messageLength, final long timestamp, final long checksum)
        {
            return 0;
        }

        public void receive()
        {
        }
    }
}
