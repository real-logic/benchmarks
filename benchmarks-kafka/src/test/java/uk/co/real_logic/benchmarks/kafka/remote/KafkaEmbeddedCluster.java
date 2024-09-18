/*
 * Copyright 2015-2024 Real Logic Limited.
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
import kafka.server.KafkaRaftServer;
import kafka.tools.StorageTool;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.server.common.MetadataVersion;
import uk.co.real_logic.benchmarks.remote.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.lang.String.valueOf;

class KafkaEmbeddedCluster implements AutoCloseable
{
    private final Path logDir;
    private final KafkaRaftServer kafka;

    KafkaEmbeddedCluster(final int httpPort, final int sslPort, final int controllerPort, final Path tempDir)
        throws Exception
    {
        logDir = tempDir.resolve("log-dir");
        Files.createDirectory(logDir);

        final Uuid clusterId = Uuid.randomUuid();
        final KafkaConfig config = createConfig(httpPort, sslPort, controllerPort);

        StorageTool.formatCommand(
            System.out,
            StorageTool.configToLogDirectories(config),
            StorageTool.buildMetadataProperties(clusterId.toString(), config),
            MetadataVersion.IBP_3_7_IV4,
            false);

        kafka = new KafkaRaftServer(config, Time.SYSTEM);
        kafka.startup();
    }

    private KafkaConfig createConfig(final int httpPort, final int sslPort, final int controllerPort)
    {
        final Properties props = new Properties();
        final int nodeId = 1;


        props.put("process.roles", "broker,controller");
        props.put("controller.quorum.voters", "1@localhost:" + controllerPort);
        props.put("controller.listener.names", "CONTROLLER");
        props.put("listeners",
            "PLAINTEXT://localhost:" + httpPort +
            ",SSL://localhost:" + sslPort +
            ",CONTROLLER://localhost:" + controllerPort);
        props.put("listener.security.protocol.map",
            "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,SSL:SSL,SASL_PLAINTEXT:SASL_PLAINTEXT,SASL_SSL:SASL_SSL");
        props.put("node.id", nodeId);
        props.put("broker.id", nodeId);
        props.put("log.dir", logDir.toAbsolutePath().toString());
        props.put("advertised.listeners",
            "PLAINTEXT://localhost:" + httpPort + ",SSL://localhost:" + sslPort);
        final Path certificatesPath = Configuration.tryResolveCertificatesDirectory();
        props.put("ssl.truststore.location",
            certificatesPath.resolve("truststore.p12").toString());
        props.put("ssl.truststore.type", "PKCS12");
        props.put("ssl.truststore.password", "truststore");
        props.put("ssl.keystore.location",
            certificatesPath.resolve("server.keystore").toString());
        props.put("ssl.keystore.type", "PKCS12");
        props.put("ssl.keystore.password", "server");
        props.put("ssl.client.auth", "required");
        props.put("auto.create.topics.enable", valueOf(true));
        props.put("message.max.bytes", valueOf(1000000));
        props.put("controlled.shutdown.enable", valueOf(true));
        props.put("log.message.downconversion.enable", valueOf(false));
        props.put("num.partitions", valueOf(1));
        props.put("default.replication.factor", valueOf(1));
        props.put("offsets.topic.replication.factor", valueOf(1));
        props.put("num.network.threads", valueOf(1));
        props.put("num.io.threads", valueOf(1));
        props.put("background.threads", valueOf(1));
        props.put("log.cleaner.threads", valueOf(1));
        props.put("num.recovery.threads.per.data.dir", valueOf(1));
        props.put("num.replica.alter.log.dirs.threads", valueOf(1));

        return new KafkaConfig(props);
    }

    public void close()
    {
        kafka.shutdown();
        kafka.awaitShutdown();
    }
}
