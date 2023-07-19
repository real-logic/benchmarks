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

import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.service.ClusteredServiceContainer;
import org.agrona.CloseHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClusterFailoverManager implements FailoverManager
{
    private final ExecutorService executor;
    private volatile ConsensusModule consensusModule;
    private volatile ClusteredServiceContainer clusteredServiceContainer;

    public ClusterFailoverManager()
    {
        executor = Executors.newSingleThreadExecutor(runnable ->
        {
            final Thread thread = new Thread(runnable, "FailoverManager");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void stepDown()
    {
        executor.execute(this::doStepDown);
    }

    private void doStepDown()
    {
        final ConsensusModule consensusModule = this.consensusModule;
        final ClusteredServiceContainer clusteredServiceContainer = this.clusteredServiceContainer;

        if (consensusModule == null || clusteredServiceContainer == null)
        {
            throw new IllegalStateException("Not initialised yet");
        }

        CloseHelper.closeAll(consensusModule, clusteredServiceContainer);
    }

    public void setConsensusModule(final ConsensusModule consensusModule)
    {
        this.consensusModule = consensusModule;
    }

    public void setClusteredServiceContainer(final ClusteredServiceContainer clusteredServiceContainer)
    {
        this.clusteredServiceContainer = clusteredServiceContainer;
    }
}
