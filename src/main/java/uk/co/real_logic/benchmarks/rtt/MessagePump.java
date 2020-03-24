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

import static java.util.Objects.requireNonNull;

/**
 * {@code MessagePump} is an SPI to be implemented by the system under test.
 */
public abstract class MessagePump
{

    private final MessageRecorder messageRecorder;

    public MessagePump(final MessageRecorder messageRecorder)
    {
        this.messageRecorder = requireNonNull(messageRecorder);
    }

    /**
     * Initialize state and establish all necessary connections.
     *
     * @param configuration configuration options
     * @throws Exception in case of an error
     * @implNote The method should block until it is safe to call {@link #send(int, int, long)}
     * and {@link #receive()} methods.
     */
    public abstract void init(Configuration configuration) throws Exception;

    /**
     * Cleanup resources and tear down the connections with remote side.
     *
     * @throws Exception in case of an error
     */
    public abstract void destroy() throws Exception;

    /**
     * Sends specified number of {@code messages} with the given {@code length} and a {@code timestamp} as payload.
     *
     * @param numberOfMessages to be sent.
     * @param length           in bytes of a single message.
     * @param timestamp        to be included in the message payload.
     * @return actual number of messages sent
     * @implSpec {@code Sender} must send a message with the payload that is at least {@code length} bytes long and
     * <em>must</em> include given {@code timestamp} in it. Any header added by the sender <em>may not</em> be
     * counted towards the {@code length}.
     * @implNote The implementation can re-try actual send operation multiple times if needed but it
     * <em>should not</em> block forever since test rig will re-try sending the batch, e.g. if a first call sends
     * only {code 3} out of {code 5} messages then there will be a second call with the batch length of {code 2}.
     * only {code 3} out of {code 5} messages then there will be a second call with the batch length of {code 2}.
     */
    public abstract int send(int numberOfMessages, int length, long timestamp);

    /**
     * Receive one or more messages.
     *
     * @return number of messages received
     * @implSpec For every received message the {@link #onMessageReceived(long)} method must be called.
     * @implNote Can be a blocking call. Invoked by the test rig in a separate receiver thread.
     */
    public abstract int receive();

    /**
     * Callback method to be invoked for every message received.
     *
     * @param timestamp payload of the received message.
     */
    protected final void onMessageReceived(final long timestamp)
    {
        messageRecorder.record(timestamp);
    }
}
