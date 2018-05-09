/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.sweep.queue;

import java.util.Collection;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

public final class KvsSweepQueueReader implements SweepQueueReader {
    private final SweepableCells sweepableCells;
    private final SweepableTimestamps sweepableTimestamps;
    private final KvsSweepQueueProgress progress;
    private final ShardAndStrategy shardStrategy;
    private final KvsSweepQueueScrubber scrubber;

    private KvsSweepQueueReader(SweepableCells sweepableCells, SweepableTimestamps timestamps,
            KvsSweepQueueProgress progress, ShardAndStrategy shardStrategy) {
        this.sweepableCells = sweepableCells;
        this.sweepableTimestamps = timestamps;
        this.progress = progress;
        this.shardStrategy = shardStrategy;
        this.scrubber = new KvsSweepQueueScrubber(sweepableCells, timestamps, progress);
    }

    public static KvsSweepQueueReader create(KvsSweepQueueTables tables, ShardAndStrategy shardStrat) {
        return new KvsSweepQueueReader(tables.sweepableCells, tables.sweepableTimestamps, tables.progress, shardStrat);
    }

    @Override
    public void consumeNextBatch(Consumer<Collection<WriteInfo>> consumer, long maxTsExclusive) {
        long previousLastSweptTs = progress.getLastSweptTimestamp(shardStrategy);
        SweepBatch sweepBatch = getNextBatchAndSweptTimestamp(previousLastSweptTs, maxTsExclusive);
        consumer.accept(sweepBatch.writes());
        scrubber.scrub(shardStrategy, previousLastSweptTs, sweepBatch.lastSweptTimestamp());
    }

    private SweepBatch getNextBatchAndSweptTimestamp(long lastSweptTs, long sweepTs) {
        return sweepableTimestamps.nextSweepableTimestampPartition(shardStrategy, lastSweptTs, sweepTs)
                .map(fine -> sweepableCells.getBatchForPartition(shardStrategy, fine, lastSweptTs, sweepTs))
                .orElse(SweepBatch.of(ImmutableList.of(), sweepTs - 1L));
    }
}