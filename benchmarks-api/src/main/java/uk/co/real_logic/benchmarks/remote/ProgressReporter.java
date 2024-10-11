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
package uk.co.real_logic.benchmarks.remote;

interface ProgressReporter
{
    ProgressReporter NULL_PROGRESS_REPORTER = new ProgressReporter()
    {
        public void reportProgress(
            final long startTimeNs, final long nowNs, final long sentMessages, final int iterations)
        {
        }

        public void reset()
        {
        }
    };

    void reportProgress(long startTimeNs, long nowNs, long sentMessages, int iterations);

    void reset();
}
