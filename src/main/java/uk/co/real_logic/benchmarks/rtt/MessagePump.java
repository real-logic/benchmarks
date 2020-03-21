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

/**
 * {@code MessagePump} is an SPI to be implemented by the system under test in order to for it to be used in the
 * {@link LoadTestRig}.
 */
public interface MessagePump
{
    /**
     * Initialize state and establish all necessary connections.
     *
     * @param configuration configuration options
     * @throws Exception in case of an error
     * @implNote The method should block until it is safe to call {@link #sender()} and {@link #receiver()} methods.
     */
    void init(Configuration configuration) throws Exception;

    /**
     * Cleanup resources and tear down the connections with remote side.
     *
     * @throws Exception in case of an error
     */
    void destroy() throws Exception;

    /**
     * Message sender.
     *
     * @return message sender
     */
    Sender sender();

    /**
     * Message receiver.
     *
     * @return message receiver
     */
    Receiver receiver();

    /**
     * {@code Sender} is invoked by the harness to send a batch of messages.
     */
    interface Sender
    {
        /**
         * Sends specified number of {@code messages} with the given {@code length} and a {@code timestamp} as payload.
         *
         * @param numberOfMessages number of messages to be sent.
         * @param length           in bytes of a single message.
         * @param timestamp        to be included as the part of the message payload.
         * @return actual number of messages sent
         * @implSpec {@code Sender} must send a message with the payload that is at least {@code length} bytes long and
         * <em>must</em> include given {@code timestamp} in it. Any header added by the sender <em>may not</em> be
         * counted towards the {@code length}.
         * @implNote The implementation can re-try actual send operation multiple times if needed but it should not block
         * forever since harness also has a re-try mechanism for the entire batch, e.g. if a first call sends {code 3} out
         * of {code 5} messages then harness will perform second call with the batch length ({@code messages}) of {code 2}.
         */
        int send(int numberOfMessages, int length, long timestamp);
    }

    /**
     * {@code Receiver} is invoked by the harness to receive echoed messages that were sent by the
     * {@link Sender} and echoed back by the remote side.
     */
    interface Receiver
    {
        /**
         * Receive a single message.
         *
         * @return {@code timestamp} payload of the message
         * @implNote Can be a blocking call. Invoked by the harness in a separate receiver thread.
         */
        long receive();
    }
}
