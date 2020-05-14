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

import org.agrona.concurrent.BusySpinIdleStrategy;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.other.benchmark.impl.MessageTransceiverFromAnotherPackage;
import uk.co.real_logic.benchmarks.remote.nested.NestedMessageTransceiver;
import uk.co.real_logic.benchmarks.remotesibling.MessageTransceiverFromSiblingPackage;
import uk.co.real_logic.benchmarks.remotesibling.nested.NestedSiblingMessageTransceiver;

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
            .warmUpIterations(-1);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("Warm-up iterations cannot be less than 0, got: -1", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { -6, 0 })
    void throwsIllegalArgumentExceptionIfWarmUpNumberOfMessagesIsInvalid(final int warmUpNumberOfMessages)
    {
        final Builder builder = new Builder()
            .warmUpIterations(3)
            .warmUpNumberOfMessages(warmUpNumberOfMessages);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("Warm-up number of messages cannot be less than 1, got: " + warmUpNumberOfMessages, ex
            .getMessage());
    }

    @Test
    void warmUpNumberOfMessagesCanBeZeroIfWarmUpIterationsIsZero()
    {
        final Configuration configuration = new Builder()
            .warmUpIterations(0)
            .warmUpNumberOfMessages(0)
            .numberOfMessages(1_000)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .build();

        assertEquals(0, configuration.warmUpIterations());
        assertEquals(0, configuration.warmUpNumberOfMessages());
    }

    @ParameterizedTest
    @ValueSource(ints = { -200, 0 })
    void throwsIllegalArgumentExceptionIfIterationsIsInvalid(final int iterations)
    {
        final Builder builder = new Builder()
            .iterations(iterations);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("Iterations cannot be less than 1, got: " + iterations,
            ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { -123, 0 })
    void throwsIllegalArgumentExceptionIfNumberOfMessagesIsInvalid(final int numberOfMessages)
    {
        final Builder builder = new Builder()
            .numberOfMessages(numberOfMessages);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("Number of messages cannot be less than 1, got: " + numberOfMessages, ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, 0 })
    void throwsIllegalArgumentExceptionIfBatchSizeIsInvalid(final int size)
    {
        final Builder builder = new Builder()
            .numberOfMessages(1000)
            .batchSize(size);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("Batch size cannot be less than 1, got: " + size, ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource("messageSizes")
    void throwsIllegalArgumentExceptionIfMessageLengthIsLessThanMinimumSize(final int length)
    {
        final Builder builder = new Builder()
            .numberOfMessages(200)
            .messageLength(length);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("Message length cannot be less than " + MIN_MESSAGE_LENGTH + ", got: " + length,
            ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfMessageTransceiverClassIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10);

        final NullPointerException ex = assertThrows(NullPointerException.class, builder::build);

        assertEquals("MessageTransceiver class cannot be null", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessageTransceiverClassIsAnAbstractClass()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messageTransceiverClass(MessageTransceiver.class);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("MessageTransceiver class must be a concrete class", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessageTransceiverClassHasNoPublicConstructor()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messageTransceiverClass(TestNoPublicConstructorMessageTransceiver.class);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("MessageTransceiver class must have a public constructor with a MessageRecorder parameter",
            ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfSenderIdleStrategyIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .sendIdleStrategy(null);

        final NullPointerException ex = assertThrows(NullPointerException.class, builder::build);

        assertEquals("Send IdleStrategy cannot be null", ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfReceiverIdleStrategyIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .receiveIdleStrategy(null);

        final NullPointerException ex = assertThrows(NullPointerException.class, builder::build);

        assertEquals("Receive IdleStrategy cannot be null", ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfOutputDirectoryIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(4)
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
            .numberOfMessages(4)
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
            .numberOfMessages(4)
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
            .numberOfMessages(4)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(outputDirectory);

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);

        assertEquals("failed to create output directory: " + outputDirectory, ex.getMessage());
    }

    @Test
    void outputFileNamePrefixShortensTransceiverClassNameByRemovingCommonPackagePrefix(final @TempDir Path tempDir)
    {
        final Configuration configuration = new Builder()
            .numberOfMessages(12)
            .batchSize(3)
            .messageLength(75)
            .messageTransceiverClass(NestedMessageTransceiver.class)
            .outputDirectory(tempDir)
            .systemProperties(props("E", "m*c^2"))
            .build();

        assertEquals(
            "nested.NestedMessageTransceiver_12_3_75_a2bea3034417edbbe21e66dd9b68d43fe53e287e04a1f6b119741ab9e0729f60",
            configuration.outputFileNamePrefix());
    }

    @Test
    void outputFileNamePrefixShortensTransceiverClassNameByRemovingCommonPackagePrefixWhenInSiblingPackage(
        final @TempDir Path tempDir)
    {
        final Configuration configuration = new Builder()
            .numberOfMessages(8)
            .batchSize(4)
            .messageLength(25)
            .messageTransceiverClass(MessageTransceiverFromSiblingPackage.class)
            .outputDirectory(tempDir)
            .systemProperties(new Properties())
            .build();

        assertEquals("remotesibling.MessageTransceiverFromSiblingPackage_8_4_25" +
            "_e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            configuration.outputFileNamePrefix());
    }

    @Test
    void outputFileNamePrefixShortensTransceiverClassNameByRemovingCommonPackagePrefixWhenNestedInSiblingPackage(
        final @TempDir Path tempDir)
    {
        final Configuration configuration = new Builder()
            .numberOfMessages(100)
            .batchSize(1)
            .messageLength(100)
            .messageTransceiverClass(NestedSiblingMessageTransceiver.class)
            .outputDirectory(tempDir)
            .systemProperties(new Properties())
            .build();

        assertEquals("remotesibling.nested.NestedSiblingMessageTransceiver_100_1_100" +
            "_e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            configuration.outputFileNamePrefix());
    }

    @Test
    void outputFileNamePrefixUsesFullyQualifiedTransceiverClassNameWhenFromAnotherPackage(final @TempDir Path tempDir)
    {
        final Configuration configuration = new Builder()
            .numberOfMessages(100)
            .messageTransceiverClass(MessageTransceiverFromAnotherPackage.class)
            .outputDirectory(tempDir)
            .systemProperties(props("x", "5"))
            .build();

        assertEquals(MessageTransceiverFromAnotherPackage.class.getName() + "_100_1_16" +
            "_29f2394eb92d0ded9247b8d7188ebddae3e13c71ebcf939302619b29604486b0",
            configuration.outputFileNamePrefix());
    }

    @Test
    void defaultOptions()
    {
        final Configuration configuration = new Builder()
            .numberOfMessages(123)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .systemProperties(new Properties())
            .build();

        assertEquals(123, configuration.numberOfMessages());
        assertEquals(DEFAULT_WARM_UP_NUMBER_OF_MESSAGES, configuration.warmUpNumberOfMessages());
        assertEquals(DEFAULT_WARM_UP_ITERATIONS, configuration.warmUpIterations());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BATCH_SIZE, configuration.batchSize());
        assertEquals(MIN_MESSAGE_LENGTH, configuration.messageLength());
        assertSame(InMemoryMessageTransceiver.class, configuration.messageTransceiverClass());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.sendIdleStrategy());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.receiveIdleStrategy());
        assertEquals(Paths.get("results").toAbsolutePath(), configuration.outputDirectory());
        assertEquals("InMemoryMessageTransceiver_123_" + DEFAULT_BATCH_SIZE + "_" + MIN_MESSAGE_LENGTH +
            "_e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            configuration.outputFileNamePrefix());
    }

    @Test
    void explicitOptions(final @TempDir Path tempDir)
    {
        final Path outputDirectory = tempDir.resolve("my-output-dir");
        final Configuration configuration = new Builder()
            .warmUpNumberOfMessages(222)
            .warmUpIterations(3)
            .iterations(11)
            .numberOfMessages(666)
            .batchSize(4)
            .messageLength(119)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .sendIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .receiveIdleStrategy(YieldingIdleStrategy.INSTANCE)
            .outputDirectory(outputDirectory)
            .build();

        assertEquals(3, configuration.warmUpIterations());
        assertEquals(222, configuration.warmUpNumberOfMessages());
        assertEquals(11, configuration.iterations());
        assertEquals(666, configuration.numberOfMessages());
        assertEquals(4, configuration.batchSize());
        assertEquals(119, configuration.messageLength());
        assertSame(InMemoryMessageTransceiver.class, configuration.messageTransceiverClass());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.sendIdleStrategy());
        assertSame(YieldingIdleStrategy.INSTANCE, configuration.receiveIdleStrategy());
        assertEquals(outputDirectory.toAbsolutePath(), configuration.outputDirectory());
    }

    @Test
    void toStringPrintsConfiguredValues()
    {
        final Configuration configuration = new Builder()
            .warmUpIterations(4)
            .warmUpNumberOfMessages(3)
            .iterations(10)
            .numberOfMessages(777)
            .batchSize(2)
            .messageLength(64)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .sendIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .receiveIdleStrategy(YieldingIdleStrategy.INSTANCE)
            .systemProperties(props("java", "25"))
            .build();

        assertEquals("Configuration{" +
            "\n    warmUpIterations=4" +
            "\n    warmUpNumberOfMessages=3" +
            "\n    iterations=10" +
            "\n    numberOfMessages=777" +
            "\n    batchSize=2" +
            "\n    messageLength=64" +
            "\n    messageTransceiverClass=uk.co.real_logic.benchmarks.remote.InMemoryMessageTransceiver" +
            "\n    sendIdleStrategy=NoOpIdleStrategy{}" +
            "\n    receiveIdleStrategy=YieldingIdleStrategy{}" +
            "\n    outputDirectory=" + Paths.get("results").toAbsolutePath() +
            "\n    outputFileNamePrefix=InMemoryMessageTransceiver_777_2_64" +
            "_73ccec448ba12264acb12e7f9f36fddc73e8c62e43549b786a901c88891610c9" +
            "\n}",
            configuration.toString());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesIsNotConfigured()
    {
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("property '" + MESSAGES_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesHasInvalidValue()
    {
        setProperty(MESSAGES_PROP_NAME, "100x000");

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("non-integer value for property '" + MESSAGES_PROP_NAME +
            "', cause: 'x' is not a valid digit @ 3", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfMessageTransceiverPropertyIsNotConfigured()
    {
        setProperty(MESSAGES_PROP_NAME, "100");

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("property '" + MESSAGE_TRANSCEIVER_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfMessageTransceiverHasInvalidValue()
    {
        setProperty(MESSAGES_PROP_NAME, "20");
        setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, Integer.class.getName());

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("invalid class value for property '" + MESSAGE_TRANSCEIVER_PROP_NAME +
            "', cause: class java.lang.Integer", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesDefaults()
    {
        setProperty(MESSAGES_PROP_NAME, "42");
        setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, InMemoryMessageTransceiver.class.getName());

        final Configuration configuration = fromSystemProperties();

        assertEquals(42, configuration.numberOfMessages());
        assertEquals(DEFAULT_WARM_UP_NUMBER_OF_MESSAGES, configuration.warmUpNumberOfMessages());
        assertEquals(DEFAULT_WARM_UP_ITERATIONS, configuration.warmUpIterations());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BATCH_SIZE, configuration.batchSize());
        assertEquals(MIN_MESSAGE_LENGTH, configuration.messageLength());
        assertSame(InMemoryMessageTransceiver.class, configuration.messageTransceiverClass());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.sendIdleStrategy());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.receiveIdleStrategy());
        assertEquals(Paths.get("results").toAbsolutePath(), configuration.outputDirectory());
    }

    @Test
    void fromSystemPropertiesOverrideAll(final @TempDir Path tempDir)
    {
        setProperty(WARM_UP_ITERATIONS_PROP_NAME, "2");
        setProperty(WARM_UP_MESSAGES_PROP_NAME, "10");
        setProperty(ITERATIONS_PROP_NAME, "4");
        setProperty(MESSAGES_PROP_NAME, "200");
        setProperty(BATCH_SIZE_PROP_NAME, "3");
        setProperty(MESSAGE_LENGTH_PROP_NAME, "24");
        setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, InMemoryMessageTransceiver.class.getName());
        setProperty(SEND_IDLE_STRATEGY_PROP_NAME, YieldingIdleStrategy.class.getName());
        setProperty(RECEIVE_IDLE_STRATEGY_PROP_NAME, BusySpinIdleStrategy.class.getName());
        final Path outputDirectory = tempDir.resolve("my-output-dir-prop");
        setProperty(OUTPUT_DIRECTORY_PROP_NAME, outputDirectory.toAbsolutePath().toString());

        final Configuration configuration = fromSystemProperties();

        assertEquals(2, configuration.warmUpIterations());
        assertEquals(10, configuration.warmUpNumberOfMessages());
        assertEquals(4, configuration.iterations());
        assertEquals(200, configuration.numberOfMessages());
        assertEquals(3, configuration.batchSize());
        assertEquals(24, configuration.messageLength());
        assertSame(InMemoryMessageTransceiver.class, configuration.messageTransceiverClass());
        assertTrue(configuration.sendIdleStrategy() instanceof YieldingIdleStrategy);
        assertTrue(configuration.receiveIdleStrategy() instanceof BusySpinIdleStrategy);
        assertEquals(outputDirectory.toAbsolutePath(), configuration.outputDirectory());
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
            arguments(props("\uD83E\uDD37", "42", "B", "2", "X", "-100", "y", "2.25", "z", "0"),
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
            WARM_UP_ITERATIONS_PROP_NAME,
            WARM_UP_MESSAGES_PROP_NAME,
            ITERATIONS_PROP_NAME,
            MESSAGES_PROP_NAME,
            BATCH_SIZE_PROP_NAME,
            MESSAGE_LENGTH_PROP_NAME,
            MESSAGE_TRANSCEIVER_PROP_NAME,
            SEND_IDLE_STRATEGY_PROP_NAME,
            RECEIVE_IDLE_STRATEGY_PROP_NAME,
            OUTPUT_DIRECTORY_PROP_NAME)
            .forEach(System::clearProperty);
    }

    private static IntStream messageSizes()
    {
        return IntStream.range(-1, MIN_MESSAGE_LENGTH);
    }

    public static final class TestNoPublicConstructorMessageTransceiver extends MessageTransceiver
    {
        private TestNoPublicConstructorMessageTransceiver(final MessageRecorder messageRecorder)
        {
            super(messageRecorder);
        }

        public void init(final Configuration configuration)
        {
        }

        public void destroy()
        {
        }

        public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
        {
            return 0;
        }

        public void receive()
        {
        }
    }
}
