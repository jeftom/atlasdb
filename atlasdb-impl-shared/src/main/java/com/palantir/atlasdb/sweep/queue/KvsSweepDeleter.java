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
import java.util.HashMap;
import java.util.Map;

import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.sweep.Sweeper;

public class KvsSweepDeleter implements SweepDeleter {
    private final KeyValueService kvs;
    private final Sweeper sweeper;

    public KvsSweepDeleter(KeyValueService kvs, Sweeper sweeper) {
        this.kvs = kvs;
        this.sweeper = sweeper;
    }

    @Override
    public void sweep(Collection<WriteInfo> writes) {
        Map<TableReference, Map<Cell, Long>> maxTimestampByCell = partitionWrites(writes);
        for (Map.Entry<TableReference, Map<Cell, Long>> entry: maxTimestampByCell.entrySet()) {
            if (sweeper.shouldAddSentinels()) {
                kvs.addGarbageCollectionSentinelValues(entry.getKey(), entry.getValue().keySet());
            }
            kvs.deleteAllTimestamps(entry.getKey(), entry.getValue());
        }
    }

    private Map<TableReference, Map<Cell, Long>> partitionWrites(Collection<WriteInfo> writes) {
        Map<TableReference, Map<Cell, Long>> result = new HashMap<>();
        writes.forEach(write -> result.computeIfAbsent(write.writeRef().tableRef(), ignore -> new HashMap<>())
                        .put(write.writeRef().cell(), write.timestampToDeleteAtExclusive(sweeper)));
        return result;
    }

    Sweeper getSweeper() {
        return sweeper;
    }
}