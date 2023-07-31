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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.FAILOVER_CONTROL_ENDPOINTS_PROP_NAME;

public final class FailoverConfiguration
{
    private final List<InetSocketAddress> controlEndpoints;

    private FailoverConfiguration(final Builder builder)
    {
        this.controlEndpoints = new ArrayList<>(builder.controlEndpoints);
    }

    public List<InetSocketAddress> controlEndpoints()
    {
        return Collections.unmodifiableList(controlEndpoints);
    }

    public static final class Builder
    {
        private List<InetSocketAddress> controlEndpoints;

        public Builder controlEndpoints(final List<InetSocketAddress> controlEndpoints)
        {
            this.controlEndpoints = controlEndpoints;
            return this;
        }

        public FailoverConfiguration build()
        {
            return new FailoverConfiguration(this);
        }
    }

    public static FailoverConfiguration fromSystemProperties()
    {
        final Builder builder = new Builder();

        final String controlEndpoints = System.getProperty(FAILOVER_CONTROL_ENDPOINTS_PROP_NAME);
        if (controlEndpoints == null || controlEndpoints.isEmpty())
        {
            throw new IllegalStateException(FAILOVER_CONTROL_ENDPOINTS_PROP_NAME + " must be set");
        }
        builder.controlEndpoints(parseEndpoints(controlEndpoints));

        return builder.build();
    }

    private static List<InetSocketAddress> parseEndpoints(final String endpoints)
    {
        final String[] endpointsArray = endpoints.split(",");

        final List<InetSocketAddress> result = new ArrayList<>(endpointsArray.length);

        for (final String endpoint : endpointsArray)
        {
            result.add(parseEndpoint(endpoint));
        }

        return result;
    }

    private static InetSocketAddress parseEndpoint(final String endpoint)
    {
        final int separator = endpoint.lastIndexOf(':');

        if (separator == -1)
        {
            throw new IllegalArgumentException("endpoint must be in <hostname>:<port> format");
        }

        final String hostname = endpoint.substring(0, separator);
        final int port = Integer.parseInt(endpoint.substring(separator + 1));

        return new InetSocketAddress(hostname, port);
    }
}
