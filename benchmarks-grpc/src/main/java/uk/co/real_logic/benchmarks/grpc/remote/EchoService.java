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
package uk.co.real_logic.benchmarks.grpc.remote;

import io.grpc.stub.StreamObserver;
import org.agrona.LangUtil;

class EchoService extends EchoBenchmarksGrpc.EchoBenchmarksImplBase
{
    public void echo(final EchoMessage request, final StreamObserver<EchoMessage> responseObserver)
    {
        responseObserver.onNext(request);
        responseObserver.onCompleted();
    }

    public StreamObserver<EchoMessage> echoStream(final StreamObserver<EchoMessage> responseObserver)
    {
        return new StreamObserver<EchoMessage>()
        {
            public void onNext(final EchoMessage message)
            {
                responseObserver.onNext(message);
            }

            public void onError(final Throwable t)
            {
                t.printStackTrace();
                responseObserver.onCompleted();
                LangUtil.rethrowUnchecked(t);
            }

            public void onCompleted()
            {
                responseObserver.onCompleted();
            }
        };
    }
}
