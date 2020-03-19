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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.real_logic.benchmarks.rtt.Configuration.*;

class ConfigurationTest
{
    @Test
    void throwsIllegalArgumentExceptionIfWarmUpIterationsIsANegativeNumber()
    {
        final Builder builder = new Builder()
            .warmUpIterations(-1);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Warm-up iterations cannot be less than 0, got: -1", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { -6, 0 })
    void throwsIllegalArgumentExceptionIfWarmUpNumberOfMessagesIsInvalid(final int warmUpNumberOfMessages)
    {
        final Builder builder = new Builder()
            .warmUpIterations(3)
            .warmUpNumberOfMessages(warmUpNumberOfMessages);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

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
            .messageProviderClass(SampleMessageProvider.class)
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

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Iterations cannot be less than 1, got: " + iterations,
            ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { -123, 0 })
    void throwsIllegalArgumentExceptionIfNumberOfMessagesIsInvalid(final int numberOfMessages)
    {
        final Builder builder = new Builder()
            .numberOfMessages(numberOfMessages);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Number of messages cannot be less than 1, got: " + numberOfMessages, ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, 0 })
    void throwsIllegalArgumentExceptionIfBurstSizeIsInvalid(final int burstSize)
    {
        final Builder builder = new Builder()
            .numberOfMessages(1000)
            .burstSize(burstSize);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Burst size cannot be less than 1, got: " + burstSize, ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource("messageSizes")
    void throwsIllegalArgumentExceptionIfMessageSizeIsLessThanMinimumSize(final int messageSize)
    {
        final Builder builder = new Builder()
            .numberOfMessages(200)
            .messageSize(messageSize);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Message size cannot be less than " + MIN_MESSAGE_SIZE + ", got: " + messageSize, ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfMessageProviderClassIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messageProviderClass(null);

        final NullPointerException ex =
            assertThrows(NullPointerException.class, () -> builder.build());

        assertEquals("MessageProvider class cannot be null", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessageProviderClassIsAnInterface()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messageProviderClass(TestMessageProvider.class);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("MessageProvider class must be a concrete class", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessageProviderClassIsAnAbstractClass()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messageProviderClass(TestAbstractMessageProvider.class);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("MessageProvider class must be a concrete class", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessageProviderClassHasNoPublicConstructor()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messageProviderClass(TestNoPublicConstructorMessageProvider.class);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("MessageProvider class must have a public no-arg constructor", ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfSenderIdleStrategyIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(4)
            .messageProviderClass(SampleMessageProvider.class)
            .senderIdleStrategy(null);

        final NullPointerException ex =
            assertThrows(NullPointerException.class, () -> builder.build());

        assertEquals("Sender IdleStrategy cannot be null", ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfReceiverIdleStrategyIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(4)
            .messageProviderClass(SampleMessageProvider.class)
            .receiverIdleStrategy(null);

        final NullPointerException ex =
            assertThrows(NullPointerException.class, () -> builder.build());

        assertEquals("Receiver IdleStrategy cannot be null", ex.getMessage());
    }

    @Test
    void defaultOptions()
    {
        final Configuration configuration = new Builder()
            .numberOfMessages(123)
            .messageProviderClass(SampleMessageProvider.class)
            .build();

        assertEquals(123, configuration.numberOfMessages());
        assertEquals(DEFAULT_WARM_UP_NUMBER_OF_MESSAGES, configuration.warmUpNumberOfMessages());
        assertEquals(DEFAULT_WARM_UP_ITERATIONS, configuration.warmUpIterations());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BURST_SIZE, configuration.burstSize());
        assertEquals(MIN_MESSAGE_SIZE, configuration.messageSize());
        assertSame(SampleMessageProvider.class, configuration.messageProviderClass());
        assertSame(BusySpinIdleStrategy.INSTANCE, configuration.senderIdleStrategy());
        assertSame(BusySpinIdleStrategy.INSTANCE, configuration.receiverIdleStrategy());
    }

    @Test
    void explicitOptions()
    {
        final Configuration configuration = new Builder()
            .warmUpNumberOfMessages(222)
            .warmUpIterations(3)
            .iterations(11)
            .numberOfMessages(666)
            .burstSize(4)
            .messageSize(119)
            .messageProviderClass(SampleMessageProvider.class)
            .senderIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .receiverIdleStrategy(YieldingIdleStrategy.INSTANCE)
            .build();

        assertEquals(3, configuration.warmUpIterations());
        assertEquals(222, configuration.warmUpNumberOfMessages());
        assertEquals(11, configuration.iterations());
        assertEquals(666, configuration.numberOfMessages());
        assertEquals(4, configuration.burstSize());
        assertEquals(119, configuration.messageSize());
        assertSame(SampleMessageProvider.class, configuration.messageProviderClass());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.senderIdleStrategy());
        assertSame(YieldingIdleStrategy.INSTANCE, configuration.receiverIdleStrategy());
    }

    @Test
    void toStringPrintsConfiguredValues()
    {
        final Configuration configuration = new Builder()
            .warmUpIterations(4)
            .warmUpNumberOfMessages(3)
            .iterations(10)
            .numberOfMessages(777)
            .burstSize(2)
            .messageSize(64)
            .messageProviderClass(SampleMessageProvider.class)
            .senderIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .receiverIdleStrategy(YieldingIdleStrategy.INSTANCE)
            .build();

        assertEquals("warmUpIterations=4, warmUpNumberOfMessages=3, iterations=10, numberOfMessages=777, " +
                "burstSize=2, messageSize=64, " +
                "messageProviderClass=uk.co.real_logic.benchmarks.rtt.SampleMessageProvider, " +
                "senderIdleStrategy=NoOpIdleStrategy{}, receiverIdleStrategy=YieldingIdleStrategy{}",
            configuration.toString());
    }

    @Test
    void fromPropertiesThrowsNullPointerExceptionIfPropertiesMapIsNull()
    {
        assertThrows(NullPointerException.class, () -> fromProperties(null));
    }

    @Test
    void fromPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesIsNotConfigured()
    {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> fromProperties(emptyMap()));

        assertEquals("Property '" + MESSAGES_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesHasInvalidValue()
    {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> fromProperties(singletonMap(MESSAGES_PROP_NAME, "100x000")));

        assertEquals("Non-integer value for property '" + MESSAGES_PROP_NAME +
            "', cause: 'x' is not a valid digit @ 3", ex.getMessage());
    }

    @Test
    void fromPropertiesThrowsIllegalArgumentExceptionIfMessageProviderPropertyIsNotConfigured()
    {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> fromProperties(singletonMap(MESSAGES_PROP_NAME, "100")));

        assertEquals("Property '" + MESSAGE_PROVIDER_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromPropertiesThrowsIllegalArgumentExceptionIfMessageProviderHasInvalidValue()
    {
        final Map<String, String> properties = new HashMap<>();
        properties.put(MESSAGES_PROP_NAME, "20");
        properties.put(MESSAGE_PROVIDER_PROP_NAME, Integer.class.getName());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> fromProperties(properties));

        assertEquals("Invalid class value for property '" + MESSAGE_PROVIDER_PROP_NAME +
            "', cause: class java.lang.Integer", ex.getMessage());
    }

    @Test
    void fromPropertiesDefaults()
    {
        final Map<String, String> properties = new HashMap<>();
        properties.put(MESSAGES_PROP_NAME, "42");
        properties.put(MESSAGE_PROVIDER_PROP_NAME, SampleMessageProvider.class.getName());

        final Configuration configuration = fromProperties(properties);

        assertEquals(42, configuration.numberOfMessages());
        assertEquals(DEFAULT_WARM_UP_NUMBER_OF_MESSAGES, configuration.warmUpNumberOfMessages());
        assertEquals(DEFAULT_WARM_UP_ITERATIONS, configuration.warmUpIterations());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BURST_SIZE, configuration.burstSize());
        assertEquals(MIN_MESSAGE_SIZE, configuration.messageSize());
        assertSame(SampleMessageProvider.class, configuration.messageProviderClass());
        assertSame(BusySpinIdleStrategy.INSTANCE, configuration.senderIdleStrategy());
        assertSame(BusySpinIdleStrategy.INSTANCE, configuration.receiverIdleStrategy());
    }

    @Test
    void fromPropertiesOverrideAll()
    {
        final Map<String, String> properties = new HashMap<>();
        properties.put(WARM_UP_ITERATIONS_PROP_NAME, "2");
        properties.put(WARM_UP_MESSAGES_PROP_NAME, "10");
        properties.put(ITERATIONS_PROP_NAME, "4");
        properties.put(MESSAGES_PROP_NAME, "200");
        properties.put(BURST_SIZE_PROP_NAME, "3");
        properties.put(MESSAGE_SIZE_PROP_NAME, "24");
        properties.put(MESSAGE_PROVIDER_PROP_NAME, SampleMessageProvider.class.getName());
        properties.put(SENDER_IDLE_STRATEGY_PROP_NAME, YieldingIdleStrategy.class.getName());
        properties.put(RECEIVER_IDLE_STRATEGY_PROP_NAME, NoOpIdleStrategy.class.getName());

        final Configuration configuration = fromProperties(properties);

        assertEquals(2, configuration.warmUpIterations());
        assertEquals(10, configuration.warmUpNumberOfMessages());
        assertEquals(4, configuration.iterations());
        assertEquals(200, configuration.numberOfMessages());
        assertEquals(3, configuration.burstSize());
        assertEquals(24, configuration.messageSize());
        assertSame(SampleMessageProvider.class, configuration.messageProviderClass());
        assertTrue(YieldingIdleStrategy.class.isInstance(configuration.senderIdleStrategy()));
        assertTrue(NoOpIdleStrategy.class.isInstance(configuration.receiverIdleStrategy()));
    }

    private static IntStream messageSizes()
    {
        return IntStream.range(-1, MIN_MESSAGE_SIZE);
    }

    private interface TestMessageProvider extends MessageProvider
    {
    }

    private abstract class TestAbstractMessageProvider implements MessageProvider
    {
    }

    private class TestNoPublicConstructorMessageProvider implements MessageProvider
    {
        private TestNoPublicConstructorMessageProvider()
        {
        }

        public void init(final Configuration configuration) throws Exception
        {
        }

        public void destroy() throws Exception
        {
        }

        public Sender sender()
        {
            return null;
        }

        public Receiver receiver()
        {
            return null;
        }
    }
}