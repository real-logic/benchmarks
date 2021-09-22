/*
 * Copyright 2015-2021 Real Logic Limited.
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
package uk.co.real_logic.benchmarks.kafka.remote;

import kafka.server.KafkaConfig;
import kafka.server.KafkaConfig$;
import kafka.server.KafkaServer;
import org.apache.kafka.common.utils.Time;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import scala.Option;
import uk.co.real_logic.benchmarks.remote.Configuration;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.lang.String.valueOf;
import static org.agrona.CloseHelper.closeAll;

class KafkaEmbeddedCluster implements AutoCloseable
{
    private final int port;
    private final Path logDir;
    private final KafkaServer kafka;
    private final NIOServerCnxnFactory factory;
    private final ZooKeeperServer zookeeper;

    KafkaEmbeddedCluster(final int zookeeperPort, final int httpPort, final int sslPort, final Path tempDir)
        throws Exception
    {
        this.port = httpPort;

        logDir = tempDir.resolve("log-dir");
        Files.createDirectory(logDir);

        final Path zookeeperDir = tempDir.resolve("zookeeper");
        final Path dataDir = zookeeperDir.resolve("data-dir");
        final Path snapshotDir = zookeeperDir.resolve("snapshot-dir");
        Files.createDirectories(dataDir);
        Files.createDirectories(snapshotDir);

        zookeeper = new ZooKeeperServer(dataDir.toFile(), snapshotDir.toFile(), 30000);
        zookeeper.setMinSessionTimeout(-1);
        zookeeper.setMaxSessionTimeout(-1);
        factory = new NIOServerCnxnFactory();
        factory.configure(new InetSocketAddress("localhost", zookeeperPort), 0);
        factory.startup(zookeeper);

        final KafkaConfig config = createConfig(httpPort, sslPort, zookeeperPort);
        kafka = new KafkaServer(config, Time.SYSTEM, Option.empty(), false);
        kafka.startup();
    }

    private KafkaConfig createConfig(final int httpPort, final int sslPort, final int zookeeperPort)
    {
        final Properties props = new Properties();

        props.put(KafkaConfig$.MODULE$.LogDirProp(), logDir.toAbsolutePath().toString());
        props.put(KafkaConfig$.MODULE$.ZkConnectProp(), "localhost:" + zookeeperPort);
        props.put(KafkaConfig$.MODULE$.ZkSessionTimeoutMsProp(), valueOf(300000));
        props.put(KafkaConfig$.MODULE$.ZkConnectionTimeoutMsProp(), valueOf(60000));
        props.put(KafkaConfig$.MODULE$.BrokerIdProp(), 0);
        final String listeners = "PLAINTEXT://localhost:" + httpPort + ",SSL://localhost:" + sslPort;
        props.put(KafkaConfig$.MODULE$.ListenersProp(), listeners);
        props.put(KafkaConfig$.MODULE$.AdvertisedListenersProp(), listeners);
        final Path certificatesPath = Configuration.tryResolveCertificatesDirectory();
        props.put(KafkaConfig$.MODULE$.SslTruststoreLocationProp(),
            certificatesPath.resolve("truststore.p12").toString());
        props.put(KafkaConfig$.MODULE$.SslTruststoreTypeProp(), "PKCS12");
        props.put(KafkaConfig$.MODULE$.SslTruststorePasswordProp(), "truststore");
        props.put(KafkaConfig$.MODULE$.SslKeystoreLocationProp(),
            certificatesPath.resolve("server.keystore").toString());
        props.put(KafkaConfig$.MODULE$.SslKeystoreTypeProp(), "PKCS12");
        props.put(KafkaConfig$.MODULE$.SslKeystorePasswordProp(), "server");
        props.put(KafkaConfig$.MODULE$.SslClientAuthProp(), "required");
        props.put(KafkaConfig$.MODULE$.AutoCreateTopicsEnableProp(), valueOf(true));
        props.put(KafkaConfig$.MODULE$.MessageMaxBytesProp(), valueOf(1000000));
        props.put(KafkaConfig$.MODULE$.ControlledShutdownEnableProp(), valueOf(true));
        props.put(KafkaConfig$.MODULE$.LogMessageDownConversionEnableProp(), valueOf(false));
        props.put(KafkaConfig$.MODULE$.NumPartitionsProp(), valueOf(1));
        props.put(KafkaConfig$.MODULE$.DefaultReplicationFactorProp(), valueOf(1));
        props.put(KafkaConfig$.MODULE$.OffsetsTopicReplicationFactorProp(), valueOf(1));
        props.put(KafkaConfig$.MODULE$.NumNetworkThreadsProp(), valueOf(1));
        props.put(KafkaConfig$.MODULE$.NumIoThreadsProp(), valueOf(1));
        props.put(KafkaConfig$.MODULE$.BackgroundThreadsProp(), valueOf(1));
        props.put(KafkaConfig$.MODULE$.LogCleanerThreadsProp(), valueOf(1));
        props.put(KafkaConfig$.MODULE$.NumRecoveryThreadsPerDataDirProp(), valueOf(1));
        props.put(KafkaConfig$.MODULE$.NumReplicaAlterLogDirsThreadsProp(), valueOf(1));

        return new KafkaConfig(props);
    }

    public void close()
    {
        closeAll(
            () ->
            {
                kafka.shutdown();
                kafka.awaitShutdown();
            },
            factory::shutdown,
            () -> zookeeper.shutdown(true),
            null != zookeeper.getZKDatabase() ? () -> zookeeper.getZKDatabase().close() : null);
    }
}
