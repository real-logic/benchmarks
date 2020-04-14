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

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.real_logic.benchmarks.rtt.Configuration.*;

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
    void defaultOptions()
    {
        final Configuration configuration = new Builder()
            .numberOfMessages(123)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
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
    }

    @Test
    void explicitOptions()
    {
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
            .build();

        assertEquals("Configuration{" +
            "\n    warmUpIterations=4" +
            "\n    warmUpNumberOfMessages=3" +
            "\n    iterations=10" +
            "\n    numberOfMessages=777" +
            "\n    batchSize=2" +
            "\n    messageLength=64" +
            "\n    messageTransceiverClass=uk.co.real_logic.benchmarks.rtt.InMemoryMessageTransceiver" +
            "\n    sendIdleStrategy=NoOpIdleStrategy{}" +
            "\n    receiveIdleStrategy=YieldingIdleStrategy{}" +
            "\n}",
            configuration.toString());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesIsNotConfigured()
    {
        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("Property '" + MESSAGES_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesHasInvalidValue()
    {
        System.setProperty(MESSAGES_PROP_NAME, "100x000");

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("Non-integer value for property '" + MESSAGES_PROP_NAME +
            "', cause: 'x' is not a valid digit @ 3", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfMessageTransceiverPropertyIsNotConfigured()
    {
        System.setProperty(MESSAGES_PROP_NAME, "100");

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("Property '" + MESSAGE_TRANSCEIVER_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesThrowsIllegalArgumentExceptionIfMessageTransceiverHasInvalidValue()
    {
        System.setProperty(MESSAGES_PROP_NAME, "20");
        System.setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, Integer.class.getName());

        final IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class, Configuration::fromSystemProperties);

        assertEquals("Invalid class value for property '" + MESSAGE_TRANSCEIVER_PROP_NAME +
            "', cause: class java.lang.Integer", ex.getMessage());
    }

    @Test
    void fromSystemPropertiesDefaults()
    {
        System.setProperty(MESSAGES_PROP_NAME, "42");
        System.setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, InMemoryMessageTransceiver.class.getName());

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
    }

    @Test
    void fromSystemPropertiesOverrideAll()
    {
        System.setProperty(WARM_UP_ITERATIONS_PROP_NAME, "2");
        System.setProperty(WARM_UP_MESSAGES_PROP_NAME, "10");
        System.setProperty(ITERATIONS_PROP_NAME, "4");
        System.setProperty(MESSAGES_PROP_NAME, "200");
        System.setProperty(BATCH_SIZE_PROP_NAME, "3");
        System.setProperty(MESSAGE_LENGTH_PROP_NAME, "24");
        System.setProperty(MESSAGE_TRANSCEIVER_PROP_NAME, InMemoryMessageTransceiver.class.getName());
        System.setProperty(SEND_IDLE_STRATEGY_PROP_NAME, YieldingIdleStrategy.class.getName());
        System.setProperty(RECEIVE_IDLE_STRATEGY_PROP_NAME, BusySpinIdleStrategy.class.getName());

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
            RECEIVE_IDLE_STRATEGY_PROP_NAME
        ).forEach(System::clearProperty);
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

        public int send(final int numberOfMessages, final int messageLength, final long timestamp)
        {
            return 0;
        }

        public void receive()
        {
        }
    }
}
