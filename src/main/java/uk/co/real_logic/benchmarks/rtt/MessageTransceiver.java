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
 * {@code MessageTransceiver} is an SPI to be implemented by the system under test.
 */
public abstract class MessageTransceiver
{
    private final MessageRecorder messageRecorder;

    public MessageTransceiver(final MessageRecorder messageRecorder)
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
     * Sends specified number of {@code numberOfMessages} with the given {@code messageLength} and a {@code timestamp}
     * as a payload.
     *
     * @param numberOfMessages to be sent.
     * @param messageLength    in bytes (of a single message).
     * @param timestamp        to be included in the message payload.
     * @return actual number of messages sent.
     * @implSpec {@code Sender} must send a message with the payload that is at least {@code messageLength} bytes long
     * and <strong>must</strong> include given {@code timestamp} value. Any header added by the sender
     * <strong>may not</strong> be counted towards the {@code messageLength} bytes.
     * <p>
     * If send is <em>synchronous and blocking</em>, i.e. for every message sent there will be an immediate response
     * message, then for every received message method {@link #onMessageReceived(long)} <strong>must</strong> be called.
     * </p>
     * @implNote The implementation can re-try actual send operation multiple times if needed but it
     * <strong>should not</strong> block forever since test rig will re-try sending the batch, e.g. if a first call
     * sends only {code 3} out of {code 5} messages then there will be a second call with the batch size of {code 2}.
     */
    public abstract int send(int numberOfMessages, int messageLength, long timestamp);

    /**
     * Receive one or more messages.
     *
     * @return number of messages received.
     * @implSpec For every received message method {@link #onMessageReceived(long)} <strong>must</strong> be called.
     * @implNote Can be a no op if the send is <em>synchronous and blocking</em>.
     * @see #send(int, int, long)
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
