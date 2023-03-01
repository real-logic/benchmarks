/*
 * Copyright 2015-2023 Real Logic Limited.
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

import io.grpc.Attributes;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import org.agrona.LangUtil;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.Boolean.getBoolean;
import static java.lang.Integer.getInteger;
import static java.lang.System.getProperty;

final class GrpcConfig
{
    public static final String CLIENT_HOST_PROP_NAME = "uk.co.real_logic.benchmarks.grpc.remote.client.host";
    public static final String CLIENT_PORT_PROP_NAME = "uk.co.real_logic.benchmarks.grpc.remote.client.port";
    public static final String SERVER_HOST_PROP_NAME = "uk.co.real_logic.benchmarks.grpc.remote.server.host";
    public static final String SERVER_PORT_PROP_NAME = "uk.co.real_logic.benchmarks.grpc.remote.server.port";
    public static final String TLS_PROP_NAME = "uk.co.real_logic.benchmarks.grpc.remote.tls";
    public static final String CERTIFICATES_DIR_PROP_NAME = "uk.co.real_logic.benchmarks.grpc.remote.certificates";

    private GrpcConfig()
    {
    }

    public static ManagedChannel getServerChannel()
    {
        final NettyChannelBuilder channelBuilder = NettyChannelBuilder
            .forAddress(new InetSocketAddress(getServerHost(), getServerPort()))
            .localSocketPicker(new NettyChannelBuilder.LocalSocketPicker()
            {
                public SocketAddress createSocketAddress(final SocketAddress remoteAddress, final Attributes attrs)
                {
                    return new InetSocketAddress(getClientHost(), getClientPort());
                }
            })
            .directExecutor();
        if (getBoolean(TLS_PROP_NAME))
        {
            final Path certificatesDir = certificatesDir();
            final SslContextBuilder sslClientContextBuilder = GrpcSslContexts.forClient()
                .trustManager(certificatesDir.resolve("ca.pem").toFile())
                .keyManager(
                certificatesDir.resolve("client.pem").toFile(),
                certificatesDir.resolve("client.key").toFile());

            try
            {
                channelBuilder.sslContext(sslClientContextBuilder.build());
            }
            catch (final SSLException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }
        else
        {
            channelBuilder.usePlaintext();
        }
        return channelBuilder.build();
    }

    public static NettyServerBuilder getServerBuilder()
    {
        final NettyServerBuilder serverBuilder =
            NettyServerBuilder.forAddress(new InetSocketAddress(getServerHost(), getServerPort())).directExecutor();
        if (getBoolean(TLS_PROP_NAME))
        {
            final Path certificatesDir = certificatesDir();
            final SslContextBuilder sslClientContextBuilder = SslContextBuilder.forServer(
                certificatesDir.resolve("server.pem").toFile(), certificatesDir.resolve("server.key").toFile())
                .trustManager(certificatesDir.resolve("ca.pem").toFile())
                .clientAuth(ClientAuth.REQUIRE);
            GrpcSslContexts.configure(sslClientContextBuilder);

            try
            {
                serverBuilder.sslContext(sslClientContextBuilder.build());
            }
            catch (final SSLException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }
        return serverBuilder;
    }

    private static String getServerHost()
    {
        return getHost(SERVER_HOST_PROP_NAME);
    }

    private static int getServerPort()
    {
        return getInteger(SERVER_PORT_PROP_NAME, 13400);
    }

    private static String getClientHost()
    {
        return getHost(CLIENT_HOST_PROP_NAME);
    }

    private static int getClientPort()
    {
        return getInteger(CLIENT_PORT_PROP_NAME, 0);
    }

    private static String getHost(final String propName)
    {
        final String host = getProperty(propName);
        return null != host ? host : "127.0.0.1";
    }

    private static Path certificatesDir()
    {
        return Paths.get(getProperty(CERTIFICATES_DIR_PROP_NAME));
    }
}
