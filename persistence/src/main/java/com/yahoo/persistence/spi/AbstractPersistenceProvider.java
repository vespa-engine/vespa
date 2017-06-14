// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

import com.yahoo.document.*;
import com.yahoo.document.fieldset.AllFields;
import com.yahoo.persistence.spi.*;
import com.yahoo.persistence.spi.result.*;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract class that implements persistence provider functionality that some providers
 * may not have use for.
 */
public abstract class AbstractPersistenceProvider implements PersistenceProvider {
    @Override
    public Result initialize() {
        return new Result();
    }

    @Override
    public PartitionStateListResult getPartitionStates() {
        List<PartitionState> partitionStates = new ArrayList<PartitionState>();
        partitionStates.add(new PartitionState(PartitionState.State.UP, ""));
        return new PartitionStateListResult(partitionStates);
    }

    @Override
    public Result setClusterState(ClusterState state) {
        return new Result();
    }

    @Override
    public Result setActiveState(Bucket bucket, BucketInfo.ActiveState active) {
        return new Result();
    }


    @Override
    public RemoveResult removeIfFound(Bucket bucket, long timestamp, DocumentId id) {
        return remove(bucket, timestamp, id);
    }

    @Override
    public Result removeEntry(Bucket bucket, long timestampToRemove) {
        return new Result();
    }

    @Override
    public Result flush(Bucket bucket) {
        return new Result();
    }

    @Override
    public BucketIdListResult getModifiedBuckets() {
        return new BucketIdListResult(new ArrayList<BucketId>());
    }

    @Override
    public Result maintain(Bucket bucket, MaintenanceLevel level) {
        return new Result();
    }

    @Override
    public Result move(Bucket bucket, short partitionId) {
        return new Result();
    }

    @Override
    public UpdateResult update(Bucket bucket, long timestamp, DocumentUpdate update) {
        GetResult result = get(bucket, new AllFields(), update.getId());
        if (result.wasFound()) {
            Document doc = result.getDocument().clone();
            update.applyTo(doc);
            put(bucket, timestamp, doc);
            return new UpdateResult(result.getLastModifiedTimestamp());
        } else {
            return new UpdateResult();
        }
    }
}
