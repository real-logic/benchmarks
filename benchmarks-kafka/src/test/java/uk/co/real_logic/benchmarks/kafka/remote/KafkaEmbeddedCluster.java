/*
 * Copyright 2015-2022 Real Logic Limited.
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
import kafka.server.KafkaRaftServer;
import kafka.tools.StorageTool;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.server.common.MetadataVersion;
import scala.Option;
import uk.co.real_logic.benchmarks.remote.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static java.lang.String.valueOf;
import static org.agrona.CloseHelper.closeAll;

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
            MetadataVersion.MINIMUM_BOOTSTRAP_VERSION,
            false);

        kafka = new KafkaRaftServer(config, Time.SYSTEM, Option.empty());
        kafka.startup();
    }

    private KafkaConfig createConfig(final int httpPort, final int sslPort, final int controllerPort)
    {
        final Properties props = new Properties();
        final int nodeId = 1;


        props.put(KafkaConfig$.MODULE$.ProcessRolesProp(), "broker,controller");
        props.put(KafkaConfig$.MODULE$.QuorumVotersProp(), "1@localhost:" + controllerPort);
        props.put(KafkaConfig$.MODULE$.ControllerListenerNamesProp(), "CONTROLLER");
        props.put(KafkaConfig$.MODULE$.ListenersProp(),
            "PLAINTEXT://localhost:" + httpPort +
            ",SSL://localhost:" + sslPort +
            ",CONTROLLER://localhost:" + controllerPort);
        props.put(KafkaConfig$.MODULE$.ListenerSecurityProtocolMapProp(),
            "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,SSL:SSL,SASL_PLAINTEXT:SASL_PLAINTEXT,SASL_SSL:SASL_SSL");
        props.put(KafkaConfig$.MODULE$.NodeIdProp(), nodeId);
        props.put(KafkaConfig$.MODULE$.BrokerIdProp(), nodeId);
        props.put(KafkaConfig$.MODULE$.LogDirProp(), logDir.toAbsolutePath().toString());
        props.put(KafkaConfig$.MODULE$.AdvertisedListenersProp(),
            "PLAINTEXT://localhost:" + httpPort + ",SSL://localhost:" + sslPort);
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
            });
    }
}
