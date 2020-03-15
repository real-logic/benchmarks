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
import org.agrona.concurrent.IdleStrategy;

import static java.util.Objects.requireNonNull;

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
     * Default number of the warm-up iterations.
     */
    public static final int DEFAULT_WARM_UP_ITERATIONS = 5;

    /**
     * Default number of the messages per single warm-up iteration.
     */
    public static final int DEFAULT_WARM_UP_NUMBER_OF_MESSAGES = 1_000;

    /**
     * Default number of the measurement iterations.
     */
    public static final int DEFAULT_ITERATIONS = 30;

    /**
     * Default number of messages in a batch.
     */
    public static final int DEFAULT_BURST_SIZE = 1;

    /**
     * Minimal message size in bytes, includes enough space to hold the {@code timestamp} payload.
     */
    public static final int MIN_MESSAGE_SIZE = 8;

    private final int warmUpIterations;
    private final int iterations;
    private final int warmUpNumberOfMessages;
    private final int numberOfMessages;
    private final int burstSize;
    private final int messageSize;
    private final IdleStrategy senderIdleStrategy;
    private final IdleStrategy receiverIdleStrategy;

    private Configuration(final Builder builder)
    {
        this.warmUpIterations = checkMinValue(builder.warmUpIterations, 0, "Warm-up iterations");
        this.warmUpNumberOfMessages = checkMinValue(builder.warmUpNumberOfMessages, warmUpIterations > 0 ? 1 : 0, "Warm-up number of messages");
        this.iterations = checkMinValue(builder.iterations, 1, "Iterations");
        this.numberOfMessages = checkMinValue(builder.numberOfMessages, 1, "Number of messages");
        this.burstSize = checkMinValue(builder.burstSize, 1, "Burst size");
        this.messageSize = checkMinValue(builder.messageSize, MIN_MESSAGE_SIZE, "Message size");
        this.senderIdleStrategy = requireNonNull(builder.senderIdleStrategy, "Sender IdleStrategy cannot be null");
        this.receiverIdleStrategy = requireNonNull(builder.receiverIdleStrategy, "Receiver IdleStrategy cannot be null");
    }

    /**
     * Number of the warm-up iterations, where each iteration has a duration of one second. Warm-up iterations results
     * will be discarded.
     *
     * @return number of the warm-up iterations, defaults to {@link #DEFAULT_WARM_UP_ITERATIONS}.
     */
    public int warmUpIterations()
    {
        return warmUpIterations;
    }

    /**
     * Number of messages that must be sent every iteration during warm-up.
     *
     * @return number of messages to be sent every warm-up iteration, defaults to
     * {@link #DEFAULT_WARM_UP_NUMBER_OF_MESSAGES}.
     * @implNote Actual number of messages sent can be less than this if the underlying implementation is not capable
     * of achieving this rate.
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
     * Number of messages that must be sent every iteration during measurement.
     *
     * @return number of messages to be sent every measurement iteration
     * @implNote Actual number of messages sent can be less than this if the underlying implementation is not capable
     * of achieving this rate.
     */
    public int numberOfMessages()
    {
        return numberOfMessages;
    }

    /**
     * Number of messages to be sent at once. For example given {@link #warmUpNumberOfMessages()} of {@code 1000} and a
     * {@link #burstSize()} of {code 1} there will be one message sent every millisecond. However if the
     * {@link #burstSize()} is {@code 5} then there will be a batch of five messages sent every five milliseconds.
     *
     * @return number of messages to be sent at once, defaults to {@link #DEFAULT_BURST_SIZE}.
     */
    public int burstSize()
    {
        return burstSize;
    }

    /**
     * Size of message in bytes.
     *
     * @return size of message in bytes, defaults to {@link #MIN_MESSAGE_SIZE}.
     */
    public int messageSize()
    {
        return messageSize;
    }

    /**
     * {@link IdleStrategy} to use when sending messages.
     *
     * @return sender {@link IdleStrategy}, defaults to {@link BusySpinIdleStrategy}.
     */
    public IdleStrategy senderIdleStrategy()
    {
        return senderIdleStrategy;
    }

    /**
     * {@link IdleStrategy} to use when receiving messages.
     *
     * @return receiver {@link IdleStrategy}, defaults to {@link BusySpinIdleStrategy}.
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
            ", burstSize=" + burstSize +
            ", messageSize=" + messageSize +
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
        private int burstSize = DEFAULT_BURST_SIZE;
        private int messageSize = MIN_MESSAGE_SIZE;
        private IdleStrategy senderIdleStrategy = BusySpinIdleStrategy.INSTANCE;
        private IdleStrategy receiverIdleStrategy = BusySpinIdleStrategy.INSTANCE;

        /**
         * Set number of the warm-up iterations.
         *
         * @param warmUpIterations number of the warm-up iterations.
         * @return this for a fluent API.
         */
        public Builder warmUpIterations(final int warmUpIterations)
        {
            this.warmUpIterations = warmUpIterations;
            return this;
        }

        /**
         * Set number of messages to be sent every warm-up iteration.
         *
         * @param warmUpNumberOfMessages number of messages to be sent every warm-up iteration.
         * @return this for a fluent API.
         */
        public Builder warmUpNumberOfMessages(final int warmUpNumberOfMessages)
        {
            this.warmUpNumberOfMessages = warmUpNumberOfMessages;
            return this;
        }

        /**
         * Set number of the measurement iterations.
         *
         * @param iterations number of the measurement iterations.
         * @return this for a fluent API.
         */
        public Builder iterations(final int iterations)
        {
            this.iterations = iterations;
            return this;
        }

        /**
         * Set number of messages to be sent every warm-up iteration.
         *
         * @param numberOfMessages number of messages to be sent every warm-up iteration.
         * @return this for a fluent API.
         */
        public Builder numberOfMessages(final int numberOfMessages)
        {
            this.numberOfMessages = numberOfMessages;
            return this;
        }

        /**
         * Set number of messages to be sent at once in a single burst.
         *
         * @param burstSize number of messages to be sent at once in a single burst.
         * @return this for a fluent API.
         */
        public Builder burstSize(final int burstSize)
        {
            this.burstSize = burstSize;
            return this;
        }

        /**
         * Set size of message in bytes. Must be at least {@link #MIN_MESSAGE_SIZE} bytes, because every message must
         * contain a {@code timestamp} payload.
         *
         * @param messageSize size of message in bytes.
         * @return this for a fluent API.
         */
        public Builder messageSize(final int messageSize)
        {
            this.messageSize = messageSize;
            return this;
        }

        /**
         * Set the {@link IdleStrategy} to use when sending messages.
         *
         * @param senderIdleStrategy idle strategy to use for sending messages.
         * @return this for a fluent API.
         */
        public Builder senderIdleStrategy(final IdleStrategy senderIdleStrategy)
        {
            this.senderIdleStrategy = senderIdleStrategy;
            return this;
        }

        /**
         * Set the {@link IdleStrategy} to use when receiving messages.
         *
         * @param receiverIdleStrategy idle strategy to use for receiving messages.
         * @return this for a fluent API.
         */
        public Builder receiverIdleStrategy(final IdleStrategy receiverIdleStrategy)
        {
            this.receiverIdleStrategy = receiverIdleStrategy;
            return this;
        }

        /**
         * Create a new instance of {@link Configuration} class from this builder.
         *
         * @return a {@link Configuration} instance
         */
        public Configuration build()
        {
            return new Configuration(this);
        }
    }

    private static int checkMinValue(final int value, final int minValue, final String prefix)
    {
        if (value < minValue)
        {
            throw new IllegalArgumentException(prefix + " cannot be less than " + minValue + ", got: " + value);
        }
        return value;
    }
}
