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
package uk.co.real_logic.benchmarks.grpc.remote;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.Boolean.getBoolean;
import static java.lang.Integer.getInteger;
import static java.lang.System.getProperty;

final class GrpcConfig
{
    public static final String SERVER_HOST = "uk.co.real_logic.benchmarks.grpc.remote.server.host";
    public static final String SERVER_PORT = "uk.co.real_logic.benchmarks.grpc.remote.server.port";
    public static final String TLS = "uk.co.real_logic.benchmarks.grpc.remote.tls";

    private GrpcConfig()
    {
    }

    public static ManagedChannel getServerChannel()
    {
        final ManagedChannelBuilder<?> channelBuilder =
            ManagedChannelBuilder.forAddress(getServerHost(), getServerPort());
        if (!getBoolean(TLS))
        {
            channelBuilder.usePlaintext();
        }
        return channelBuilder.build();
    }

    public static NettyServerBuilder getServerBuilder()
    {
        final NettyServerBuilder serverBuilder =
            NettyServerBuilder.forAddress(new InetSocketAddress(getServerHost(), getServerPort()));
        if (getBoolean(TLS))
        {
            final Path userDir = Paths.get(getProperty("user.dir"));
            final Path certificatesDir;
            if (userDir.endsWith("benchmarks-grpc") || userDir.endsWith("scripts"))
            {
                certificatesDir = userDir.getParent().resolve("certificates");
            }
            else
            {
                certificatesDir = userDir.resolve("certificates");
            }
            serverBuilder.useTransportSecurity(
                certificatesDir.resolve("benchmarks.pem").toFile(),
                certificatesDir.resolve("benchmarks-key.pem").toFile());
        }
        return serverBuilder;
    }

    private static String getServerHost()
    {
        final String host = getProperty(SERVER_HOST);
        return null != host ? host : "127.0.0.1";
    }

    private static int getServerPort()
    {
        return getInteger(SERVER_PORT, 13400);
    }
}
