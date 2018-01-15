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

package com.palantir.atlasdb.performance.benchmarks;

import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.palantir.atlasdb.encoding.PtBytes;
import com.palantir.atlasdb.keyvalue.api.CandidateCellForSweeping;
import com.palantir.atlasdb.keyvalue.api.CandidateCellForSweepingRequest;
import com.palantir.atlasdb.keyvalue.api.ImmutableCandidateCellForSweepingRequest;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.keyvalue.api.TimestampFetchMode;
import com.palantir.atlasdb.keyvalue.api.Value;
import com.palantir.atlasdb.performance.benchmarks.table.ConsecutiveNarrowTable;
import com.palantir.common.base.ClosableIterator;

@State(Scope.Benchmark)
public class TempBenchmark {
    private static final int BATCH_SIZE_HINT = 16384;
    private static final TimestampFetchMode FETCH_MODE = TimestampFetchMode.GREATER_THAN;

    @Benchmark
    @Threads(1)
    @Warmup(time = 1)
    @Measurement(time = 1)
    public Object fullTableScan_Clean_Huge_GreaterThan(ConsecutiveNarrowTable.CleanNarrowTable table) {
        return fullTableScan(table, BATCH_SIZE_HINT, FETCH_MODE);
    }

    @Benchmark
    @Threads(1)
    @Warmup(time = 1)
    @Measurement(time = 1)
    public Object fullTableScan_Average_Huge_GreaterThan(ConsecutiveNarrowTable.AverageTable table) {
        return fullTableScan(table, BATCH_SIZE_HINT, FETCH_MODE);
    }

    @Benchmark
    @Threads(1)
    @Warmup(time = 1)
    @Measurement(time = 1)
    public Object fullTableScan_Dirty_Huge_GreaterThan(ConsecutiveNarrowTable.DirtyNarrowTable table) {
        return fullTableScan(table, BATCH_SIZE_HINT, FETCH_MODE);
    }


    private int fullTableScan(ConsecutiveNarrowTable table, int batchSizeHint, TimestampFetchMode fetchMode) {
        // TODO(gsheasby): consider extracting a common interface for WideRowTable and ConsecutiveNarrowTable
        // to avoid unpacking here
        int numCellsExpected = table.getNumRows() * table.getCellsPerRow(); // hack! AverageTable has 10 columns
        return fullTableScan(table.getTableRef(), table.getKvs(), numCellsExpected, false, batchSizeHint, fetchMode);
    }

    private int fullTableScan(TableReference tableRef, KeyValueService kvs, int numCellsExpected, boolean thorough,
            int batchSizeHint, TimestampFetchMode fetchMode) {
        CandidateCellForSweepingRequest request = ImmutableCandidateCellForSweepingRequest.builder()
                .startRowInclusive(PtBytes.EMPTY_BYTE_ARRAY)
                .batchSizeHint(batchSizeHint)
                .maxTimestampExclusive(Long.MAX_VALUE)
                .shouldCheckIfLatestValueIsEmpty(thorough)
                .timestampsToIgnore(thorough ? ImmutableSet.of() : ImmutableSet.of(Value.INVALID_VALUE_TIMESTAMP))
                .fetchMode(fetchMode)
                .build();
        try (ClosableIterator<List<CandidateCellForSweeping>> iter = kvs.getCandidateCellsForSweeping(
                tableRef, request)) {
            int numCandidates = Iterators.size(Iterators.concat(Iterators.transform(iter, List::iterator)));
            Preconditions.checkState(numCandidates == numCellsExpected,
                    "Number of candidates %s != %s", numCandidates, numCellsExpected);
            return numCandidates;
        }
    }
}