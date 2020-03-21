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

import sun.misc.Unsafe;

import java.util.Arrays;

import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.UnsafeAccess.UNSAFE;

public final class SampleMessagePump implements MessagePump
{
    static final int SIZE = 4096;
    private static final int MASK = SIZE - 1;
    private static final int SHIFT = 31 - Integer.numberOfLeadingZeros(Unsafe.ARRAY_LONG_INDEX_SCALE);
    private static final int PADDING = CACHE_LINE_LENGTH / SIZE_OF_LONG;
    private final long[] messages = new long[SIZE];

    public void init(final Configuration configuration) throws Exception
    {
        Arrays.fill(messages, 0L);
    }

    public void destroy() throws Exception
    {
        Arrays.fill(messages, 0L);
    }

    public Sender sender()
    {
        return new Sender()
        {
            private long index = 0;

            public int send(final int numberOfMessages, final int length, final long timestamp)
            {
                final long[] messages = SampleMessagePump.this.messages;
                final long index = this.index;
                if (0L != UNSAFE.getLongVolatile(messages, offset(index + numberOfMessages - 1)))
                {
                    return 0;
                }

                for (int i = numberOfMessages; i > 1; i--)
                {
                    UNSAFE.putLong(messages, offset(index + i - 1), timestamp);
                }
                UNSAFE.putOrderedLong(messages, offset(index), timestamp);

                this.index += numberOfMessages;
                return numberOfMessages;
            }
        };
    }

    public Receiver receiver()
    {
        return new Receiver()
        {
            private long index = 0;

            public long receive()
            {
                final long offset = offset(index);
                final long[] messages = SampleMessagePump.this.messages;
                final long timestamp = UNSAFE.getLongVolatile(messages, offset);
                if (0L != timestamp)
                {
                    UNSAFE.putOrderedLong(messages, offset, 0L);
                    index++;
                    return timestamp;
                }
                return 0;
            }
        };
    }

    private static long offset(final long index)
    {
        return (((index + PADDING) & MASK) << SHIFT) + Unsafe.ARRAY_LONG_BASE_OFFSET;
    }
}
