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

import org.agrona.AsciiEncoding;
import org.agrona.AsciiNumberFormatException;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isPublic;
import static java.util.Objects.requireNonNull;
import static joptsimple.internal.Strings.isNullOrEmpty;

/**
 * {@code Configuration} contains configuration values for the harness.
 * <p>
 * A {@code Configuration} instance can be created using the {@link Builder} class, e.g.:
 * <pre>
 *    final Configuration.Builder builder = new Configuration.Builder();
 *    build.sendRate(1000);
 *    ...
 *    final Configuration configuration = builder.build();
 * </pre>
 * </p>
 */
public final class Configuration
{
    /**
     * Default number of the warm up iterations.
     */
    public static final int DEFAULT_WARM_UP_ITERATIONS = 5;

    /**
     * Default number of messages per single warm up iteration.
     */
    public static final int DEFAULT_WARM_UP_NUMBER_OF_MESSAGES = 1_000;

    /**
     * Default number of measurement iterations.
     */
    public static final int DEFAULT_ITERATIONS = 30;

    /**
     * Default number of messages in a single batch.
     */
    public static final int DEFAULT_BATCH_SIZE = 1;

    /**
     * Minimal length in bytes of a single message. Contains enough space to hold the {@code timestamp} payload.
     */
    public static final int MIN_MESSAGE_LENGTH = 8;

    /**
     * Name of the system property to configure the number of warm up iterations. Default value is
     * {@link #DEFAULT_WARM_UP_ITERATIONS}.
     *
     * @see #warmUpIterations()
     */
    public static final String WARM_UP_ITERATIONS_PROP_NAME = "uk.co.real_logic.benchmarks.rtt.warmup.iterations";

    /**
     * Name of the system property to configure the number of messages to be sent during warm up. Default value is
     * {@link #DEFAULT_WARM_UP_NUMBER_OF_MESSAGES}.
     *
     * @see #warmUpNumberOfMessages()
     */
    public static final String WARM_UP_MESSAGES_PROP_NAME = "uk.co.real_logic.benchmarks.rtt.warmup.messages";

    /**
     * Name of the system property to configure the number of measurement iterations. Default value is
     * {@link #DEFAULT_ITERATIONS}.
     *
     * @see #iterations()
     */
    public static final String ITERATIONS_PROP_NAME = "uk.co.real_logic.benchmarks.rtt.iterations";

    /**
     * Name of the required system property to configure the number of messages to be sent during the measurement
     * iterations.
     *
     * @see #numberOfMessages()
     */
    public static final String MESSAGES_PROP_NAME = "uk.co.real_logic.benchmarks.rtt.messages";

    /**
     * Name of the system property to configure the batch size, i.e. number of messages to be sent in a single burst.
     * Default value is {@link #DEFAULT_BATCH_SIZE}.
     *
     * @see #batchSize()
     */
    public static final String BATCH_SIZE_PROP_NAME = "uk.co.real_logic.benchmarks.rtt.batch_size";

    /**
     * Name of the system property to configure the message size in bytes. Default value is {@link #MIN_MESSAGE_LENGTH}.
     *
     * @see #messageLength()
     */
    public static final String MESSAGE_LENGTH_PROP_NAME = "uk.co.real_logic.benchmarks.rtt.message_length";

    /**
     * Name of the system property to configure the {@link IdleStrategy} for the sender. Must be a fully qualified class
     * name. Default value is {@link NoOpIdleStrategy}.
     *
     * @see #senderIdleStrategy()
     */
    public static final String SENDER_IDLE_STRATEGY_PROP_NAME = "uk.co.real_logic.benchmarks.rtt.sender.idle_strategy";

    /**
     * Name of the system property to configure the {@link IdleStrategy} for the receiver. Must be a fully qualified
     * class name. Default value is {@link NoOpIdleStrategy}.
     *
     * @see #receiverIdleStrategy()
     */
    public static final String RECEIVER_IDLE_STRATEGY_PROP_NAME =
        "uk.co.real_logic.benchmarks.rtt.receiver.idle_strategy";

    /**
     * Name of the required system property to configure the {@link MessagePump} class (i.e. system under test) to be
     * used for the benchmark. Must be a fully qualified class name.
     */
    public static final String MESSAGE_PUMP_PROP_NAME = "uk.co.real_logic.benchmarks.rtt.message_pump";

    private final int warmUpIterations;
    private final int warmUpNumberOfMessages;
    private final int iterations;
    private final int numberOfMessages;
    private final int batchSize;
    private final int messageLength;
    private final Class<? extends MessagePump> messagePumpClass;
    private final IdleStrategy senderIdleStrategy;
    private final IdleStrategy receiverIdleStrategy;

    private Configuration(final Builder builder)
    {
        this.warmUpIterations = checkMinValue(builder.warmUpIterations, 0, "Warm-up iterations");
        this.warmUpNumberOfMessages =
            checkMinValue(builder.warmUpNumberOfMessages, warmUpIterations > 0 ? 1 : 0, "Warm-up number of messages");
        this.iterations = checkMinValue(builder.iterations, 1, "Iterations");
        this.numberOfMessages = checkMinValue(builder.numberOfMessages, 1, "Number of messages");
        this.batchSize = checkMinValue(builder.batchSize, 1, "Batch size");
        this.messageLength = checkMinValue(builder.messageLength, MIN_MESSAGE_LENGTH, "Message length");
        this.messagePumpClass = validateMessagePumpClass(builder.messagePumpClass);
        this.senderIdleStrategy =
            requireNonNull(builder.senderIdleStrategy, "Sender IdleStrategy cannot be null");
        this.receiverIdleStrategy =
            requireNonNull(builder.receiverIdleStrategy, "Receiver IdleStrategy cannot be null");
    }

    /**
     * Number of the warm up iterations, where each iteration has a duration of one second. Warm up iterations results
     * will be discarded.
     *
     * @return number of the warm up iterations, defaults to {@link #DEFAULT_WARM_UP_ITERATIONS}.
     */
    public int warmUpIterations()
    {
        return warmUpIterations;
    }

    /**
     * Number of messages per warm up iteration.
     *
     * @return number of messages per warm up iteration, defaults to {@link #DEFAULT_WARM_UP_NUMBER_OF_MESSAGES}.
     * @implNote Actual number of messages sent can be less than this number if the underlying system is not capable
     * of achieving the target send rate.
     */
    public int warmUpNumberOfMessages()
    {
        return warmUpNumberOfMessages;
    }

    /**
     * Number of the measurement iterations, where each iteration has a duration of one second.
     *
     * @return number of the measurement iterations, defaults to {@link #DEFAULT_ITERATIONS}.
     */
    public int iterations()
    {
        return iterations;
    }

    /**
     * Number of messages per measurement iteration.
     *
     * @return number of messages per measurement iteration.
     * @implNote Actual number of messages sent can be less than this number if the underlying system is not capable
     * of achieving the target send rate.
     */
    public int numberOfMessages()
    {
        return numberOfMessages;
    }

    /**
     * Size of the batch, i.e. number of messages to be sent in a single burst.
     * <p>
     * For example if the number of messages is {@code 1000} and the batch size is {code 1} then a single message will
     * be sent every millisecond. However if the batch size is {@code 5} then a batch of five messages will be sent
     * every five milliseconds.
     * </p>
     *
     * @return number of messages to be sent in a single burst, defaults to {@link #DEFAULT_BATCH_SIZE}.
     */
    public int batchSize()
    {
        return batchSize;
    }

    /**
     * Length in bytes of a single message.
     *
     * @return length in bytes of a single message, defaults to {@link #MIN_MESSAGE_LENGTH}.
     */
    public int messageLength()
    {
        return messageLength;
    }

    /**
     * {@link MessagePump} class to use for the benchmark.
     *
     * @return {@link MessagePump} class.
     */
    public Class<? extends MessagePump> messagePumpClass()
    {
        return messagePumpClass;
    }

    /**
     * {@link IdleStrategy} to use when sending messages.
     *
     * @return sender {@link IdleStrategy}, defaults to {@link NoOpIdleStrategy}.
     */
    public IdleStrategy senderIdleStrategy()
    {
        return senderIdleStrategy;
    }

    /**
     * {@link IdleStrategy} to use when receiving messages.
     *
     * @return receiver {@link IdleStrategy}, defaults to {@link NoOpIdleStrategy}.
     */
    public IdleStrategy receiverIdleStrategy()
    {
        return receiverIdleStrategy;
    }

    public String toString()
    {
        return "warmUpIterations=" + warmUpIterations +
            ", warmUpNumberOfMessages=" + warmUpNumberOfMessages +
            ", iterations=" + iterations +
            ", numberOfMessages=" + numberOfMessages +
            ", batchSize=" + batchSize +
            ", messageLength=" + messageLength +
            ", messagePumpClass=" + messagePumpClass.getName() +
            ", senderIdleStrategy=" + senderIdleStrategy +
            ", receiverIdleStrategy=" + receiverIdleStrategy;
    }

    /**
     * A builder for the {@code Configuration}.
     */
    public static final class Builder
    {
        private int warmUpIterations = DEFAULT_WARM_UP_ITERATIONS;
        private int warmUpNumberOfMessages = DEFAULT_WARM_UP_NUMBER_OF_MESSAGES;
        private int iterations = DEFAULT_ITERATIONS;
        private int numberOfMessages;
        private int batchSize = DEFAULT_BATCH_SIZE;
        private int messageLength = MIN_MESSAGE_LENGTH;
        private Class<? extends MessagePump> messagePumpClass;
        private IdleStrategy senderIdleStrategy = NoOpIdleStrategy.INSTANCE;
        private IdleStrategy receiverIdleStrategy = NoOpIdleStrategy.INSTANCE;

        /**
         * Set the number of warm up iterations.
         *
         * @param iterations number of warm up iterations.
         * @return this for a fluent API.
         */
        public Builder warmUpIterations(final int iterations)
        {
            this.warmUpIterations = iterations;
            return this;
        }

        /**
         * Set the number of messages per warm up iteration.
         *
         * @param numberOfMessages per warm up iteration.
         * @return this for a fluent API.
         */
        public Builder warmUpNumberOfMessages(final int numberOfMessages)
        {
            this.warmUpNumberOfMessages = numberOfMessages;
            return this;
        }

        /**
         * Set the number of measurement iterations.
         *
         * @param iterations number of measurement iterations.
         * @return this for a fluent API.
         */
        public Builder iterations(final int iterations)
        {
            this.iterations = iterations;
            return this;
        }

        /**
         * Set the number of messages per measurement iteration.
         *
         * @param numberOfMessages per measurement iteration.
         * @return this for a fluent API.
         */
        public Builder numberOfMessages(final int numberOfMessages)
        {
            this.numberOfMessages = numberOfMessages;
            return this;
        }

        /**
         * Set the batch size, i.e. number of messages to be sent at once in a single burst.
         *
         * @param size of a single batch of messages.
         * @return this for a fluent API.
         */
        public Builder batchSize(final int size)
        {
            this.batchSize = size;
            return this;
        }

        /**
         * Set the length of a single message in bytes. Must be at least {@link #MIN_MESSAGE_LENGTH} bytes long, since
         * every message must contain a {@code timestamp} payload.
         *
         * @param length of a single message in bytes.
         * @return this for a fluent API.
         */
        public Builder messageLength(final int length)
        {
            this.messageLength = length;
            return this;
        }

        /**
         * Set the {@link MessagePump} class.
         *
         * @param klass class.
         * @return this for a fluent API.
         */
        public Builder messagePumpClass(final Class<? extends MessagePump> klass)
        {
            this.messagePumpClass = klass;
            return this;
        }

        /**
         * Set the {@link IdleStrategy} for the sender.
         *
         * @param senderIdleStrategy idle strategy for the sender.
         * @return this for a fluent API.
         */
        public Builder senderIdleStrategy(final IdleStrategy senderIdleStrategy)
        {
            this.senderIdleStrategy = senderIdleStrategy;
            return this;
        }

        /**
         * Set the {@link IdleStrategy} for the receiver.
         *
         * @param receiverIdleStrategy idle strategy for the receiver.
         * @return this for a fluent API.
         */
        public Builder receiverIdleStrategy(final IdleStrategy receiverIdleStrategy)
        {
            this.receiverIdleStrategy = receiverIdleStrategy;
            return this;
        }

        /**
         * Create a new instance of the {@link Configuration} class from this builder.
         *
         * @return a {@link Configuration} instance
         */
        public Configuration build()
        {
            return new Configuration(this);
        }
    }

    /**
     * Create a {@link Configuration} instance based on the provided system properties.
     *
     * @param properties system properties.
     * @return a {@link Configuration} instance.
     * @throws NullPointerException if {@code properties == null}
     */
    public static Configuration fromProperties(final Map<String, String> properties)
    {
        requireNonNull(properties);

        final Builder builder = new Builder();
        if (isPropertyProvided(properties, WARM_UP_ITERATIONS_PROP_NAME))
        {
            builder.warmUpIterations(parseInt(properties, WARM_UP_ITERATIONS_PROP_NAME));
        }

        if (isPropertyProvided(properties, WARM_UP_MESSAGES_PROP_NAME))
        {
            builder.warmUpNumberOfMessages(parseInt(properties, WARM_UP_MESSAGES_PROP_NAME));
        }

        if (isPropertyProvided(properties, ITERATIONS_PROP_NAME))
        {
            builder.iterations(parseInt(properties, ITERATIONS_PROP_NAME));
        }

        if (isPropertyProvided(properties, BATCH_SIZE_PROP_NAME))
        {
            builder.batchSize(parseInt(properties, BATCH_SIZE_PROP_NAME));
        }

        if (isPropertyProvided(properties, MESSAGE_LENGTH_PROP_NAME))
        {
            builder.messageLength(parseInt(properties, MESSAGE_LENGTH_PROP_NAME));
        }

        if (isPropertyProvided(properties, SENDER_IDLE_STRATEGY_PROP_NAME))
        {
            builder.senderIdleStrategy(parseIdleStrategy(properties, SENDER_IDLE_STRATEGY_PROP_NAME));
        }

        if (isPropertyProvided(properties, RECEIVER_IDLE_STRATEGY_PROP_NAME))
        {
            builder.receiverIdleStrategy(parseIdleStrategy(properties, RECEIVER_IDLE_STRATEGY_PROP_NAME));
        }

        builder.numberOfMessages(parseInt(properties, MESSAGES_PROP_NAME));

        builder.messagePumpClass(parseClass(properties, MESSAGE_PUMP_PROP_NAME, MessagePump.class));

        return builder.build();
    }

    private static int checkMinValue(final int value, final int minValue, final String prefix)
    {
        if (value < minValue)
        {
            throw new IllegalArgumentException(prefix + " cannot be less than " + minValue + ", got: " + value);
        }
        return value;
    }

    private static Class<? extends MessagePump> validateMessagePumpClass(
        final Class<? extends MessagePump> klass)
    {
        requireNonNull(klass, "MessagePump class cannot be null");
        if (klass.isInterface() || isAbstract(klass.getModifiers()))
        {
            throw new IllegalArgumentException("MessagePump class must be a concrete class");
        }
        try
        {
            final Constructor<? extends MessagePump> constructor = klass.getConstructor();
            if (isPublic(constructor.getModifiers()))
            {
                return klass;
            }
        }
        catch (final NoSuchMethodException e)
        {
        }
        throw new IllegalArgumentException("MessagePump class must have a public no-arg constructor");
    }

    private static boolean isPropertyProvided(final Map<String, String> properties, final String propName)
    {
        return !isNullOrEmpty(properties.get(propName));
    }

    private static int parseInt(final Map<String, String> properties, final String propName)
    {
        try
        {
            final String value = getValue(properties, propName);
            return AsciiEncoding.parseIntAscii(value, 0, value.length());
        }
        catch (final AsciiNumberFormatException ex)
        {
            throw new IllegalArgumentException("Non-integer value for property '" + propName + "', cause: " +
                ex.getMessage());
        }
    }

    private static String getValue(final Map<String, String> properties, final String propName)
    {
        final String value = properties.get(propName);
        if (isNullOrEmpty(value))
        {
            throw new IllegalArgumentException("Property '" + propName + "' is required!");
        }
        return value;
    }

    private static <T> Class<? extends T> parseClass(
        final Map<String, String> properties, final String propName, final Class<T> parentClass)
    {
        try
        {
            final Class<?> klass = Class.forName(getValue(properties, propName));
            return klass.asSubclass(parentClass);
        }
        catch (final ClassNotFoundException | ClassCastException ex)
        {
            throw new IllegalArgumentException("Invalid class value for property '" + propName + "', cause: " +
                ex.getMessage());
        }
    }

    private static IdleStrategy parseIdleStrategy(final Map<String, String> properties, final String propName)
    {
        final Class<? extends IdleStrategy> klass = parseClass(properties, propName, IdleStrategy.class);
        try
        {
            return klass.getDeclaredConstructor().newInstance();
        }
        catch (final InstantiationException | IllegalAccessException | NoSuchMethodException ex)
        {
            throw new IllegalArgumentException("Invalid IdleStrategy property '" + propName + "', cause: " +
                ex.getMessage());
        }
        catch (final InvocationTargetException ex)
        {
            throw new IllegalArgumentException("Invalid IdleStrategy property '" + propName + "', cause: " +
                ex.getCause().getMessage());
        }
    }
}
