/*
 * Copyright 2023 Adaptive Financial Consulting Limited.
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
package uk.co.real_logic.benchmarks.aeron.remote;

import org.agrona.CloseHelper;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class Component<T extends AutoCloseable> implements AutoCloseable
{
    private final ReentrantLock lock = new ReentrantLock();
    private final Supplier<T> factory;
    private T resource;

    public Component(final Supplier<T> factory)
    {
        this.factory = requireNonNull(factory, "factory must not be null");
    }

    public Component<T> start()
    {
        lock.lock();
        try
        {
            if (resource != null)
            {
                throw new IllegalStateException("already started");
            }

            resource = factory.get();

            return this;
        }
        finally
        {
            lock.unlock();
        }
    }

    public void close()
    {
        lock.lock();
        try
        {
            if (resource != null)
            {
                try
                {
                    CloseHelper.close(resource);
                }
                finally
                {
                    resource = null;
                }
            }
        }
        finally
        {
            lock.unlock();
        }
    }
}
