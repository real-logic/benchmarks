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

import static java.lang.Integer.numberOfLeadingZeros;
import static java.util.Arrays.fill;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.UnsafeAccess.UNSAFE;
import static sun.misc.Unsafe.ARRAY_LONG_BASE_OFFSET;
import static sun.misc.Unsafe.ARRAY_LONG_INDEX_SCALE;

@SuppressWarnings("unused")
abstract class InMemoryMessageTransceiver1 extends MessageTransceiver
{
    static final int SIZE = 4096;
    private static final int MASK = SIZE - 1;
    private static final int SHIFT = 31 - numberOfLeadingZeros(ARRAY_LONG_INDEX_SCALE);
    private static final int PADDING_OFFSET = CACHE_LINE_LENGTH / SIZE_OF_LONG;
    final long[] messages = new long[SIZE];

    // Padding
    boolean p000, p001, p002, p003, p004, p005, p006, p007, p008, p009, p010, p011, p012, p013, p014, p015;
    boolean p016, p017, p018, p019, p020, p021, p022, p023, p024, p025, p026, p027, p028, p029, p030, p031;
    boolean p032, p033, p034, p035, p036, p037, p038, p039, p040, p041, p042, p043, p044, p045, p046, p047;
    boolean p048, p049, p050, p051, p052, p053, p054, p055, p056, p057, p058, p059, p060, p061, p062, p063;
    boolean p064, p065, p066, p067, p068, p069, p070, p071, p072, p073, p074, p075, p076, p077, p078, p079;
    boolean p080, p081, p082, p083, p084, p085, p086, p087, p088, p089, p090, p091, p092, p093, p094, p095;
    boolean p096, p097, p098, p099, p100, p101, p102, p103, p104, p105, p106, p107, p108, p109, p110, p111;
    boolean p112, p113, p114, p115, p116, p117, p118, p119, p120, p121, p122, p123, p124, p125, p126, p127;

    InMemoryMessageTransceiver1(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }

    static long offset(final long index)
    {
        return (((index + PADDING_OFFSET) & MASK) << SHIFT) + ARRAY_LONG_BASE_OFFSET;
    }
}

@SuppressWarnings("unused")
abstract class InMemoryMessageTransceiver2 extends InMemoryMessageTransceiver1
{
    long sendIndex = 0;

    // Padding
    boolean p000, p001, p002, p003, p004, p005, p006, p007, p008, p009, p010, p011, p012, p013, p014, p015;
    boolean p016, p017, p018, p019, p020, p021, p022, p023, p024, p025, p026, p027, p028, p029, p030, p031;
    boolean p032, p033, p034, p035, p036, p037, p038, p039, p040, p041, p042, p043, p044, p045, p046, p047;
    boolean p048, p049, p050, p051, p052, p053, p054, p055, p056, p057, p058, p059, p060, p061, p062, p063;
    boolean p064, p065, p066, p067, p068, p069, p070, p071, p072, p073, p074, p075, p076, p077, p078, p079;
    boolean p080, p081, p082, p083, p084, p085, p086, p087, p088, p089, p090, p091, p092, p093, p094, p095;
    boolean p096, p097, p098, p099, p100, p101, p102, p103, p104, p105, p106, p107, p108, p109, p110, p111;
    boolean p112, p113, p114, p115, p116, p117, p118, p119, p120, p121, p122, p123, p124, p125, p126, p127;

    InMemoryMessageTransceiver2(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }
}

public final class InMemoryMessageTransceiver extends InMemoryMessageTransceiver2
{
    private long receiveIndex = 0;

    public InMemoryMessageTransceiver(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }

    public void init(final Configuration configuration)
    {
        fill(messages, 0L);
    }

    public void destroy()
    {
        fill(messages, 0L);
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp)
    {
        final long[] messages = this.messages;
        final long index = sendIndex;
        if (0L != UNSAFE.getLongVolatile(messages, offset(index + numberOfMessages - 1)))
        {
            return 0;
        }

        for (int i = numberOfMessages; i > 1; i--)
        {
            UNSAFE.putLong(messages, offset(index + i - 1), timestamp);
        }
        UNSAFE.putOrderedLong(messages, offset(index), timestamp);

        sendIndex += numberOfMessages;

        return numberOfMessages;
    }

    public int receive()
    {
        final long offset = offset(receiveIndex);
        final long[] messages = this.messages;
        final long timestamp = UNSAFE.getLongVolatile(messages, offset);
        if (0L != timestamp)
        {
            onMessageReceived(timestamp);
            UNSAFE.putOrderedLong(messages, offset, 0L);
            receiveIndex++;
            return 1;
        }

        return 0;
    }
}
