// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.dummy;

import com.yahoo.collections.Pair;
import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.persistence.spi.AbstractPersistenceProvider;
import com.yahoo.persistence.spi.*;
import com.yahoo.persistence.spi.conformance.ConformanceTest;
import com.yahoo.persistence.spi.result.*;
import java.util.*;
import com.yahoo.document.*;

/**
 * Simple memory-based implementation of the persistence provider interface.
 * Intended as an example for future implementations.
 */
public class DummyPersistenceProvider extends AbstractPersistenceProvider
{
    Map<BucketId, BucketContents> bucketContents = new TreeMap<BucketId, BucketContents>();
    long nextIteratorId = 1;
    Map<Long, IteratorContext> iteratorContexts = new TreeMap<Long, IteratorContext>();

    @Override
    public synchronized Result initialize() {
        bucketContents.clear();
        iteratorContexts.clear();
        return new Result();
    }

    @Override
    public synchronized BucketIdListResult listBuckets(short partition) {
        return new BucketIdListResult(new ArrayList<BucketId>(bucketContents.keySet()));
    }

    @Override
    public synchronized BucketInfoResult getBucketInfo(Bucket bucket) {
        BucketContents contents = bucketContents.get(bucket.getBucketId());
        if (contents == null) {
            return new BucketInfoResult(new BucketInfo());
        }
        return new BucketInfoResult(contents.getBucketInfo());
    }

    @Override
    public synchronized Result put(Bucket bucket, long timestamp, Document doc) {
        bucketContents.get(bucket.getBucketId()).put(timestamp, doc);
        return new Result();
    }

    @Override
    public synchronized RemoveResult remove(Bucket bucket, long timestamp, DocumentId id) {
        return new RemoveResult(bucketContents.get(bucket.getBucketId()).remove(timestamp, id));
    }

    @Override
    public synchronized GetResult get(Bucket bucket, FieldSet fieldSet, DocumentId id) {
        BucketContents contents = bucketContents.get(bucket.getBucketId());
        if (contents == null) {
            return new GetResult();
        }

        return contents.get(id);
    }

    @Override
    public synchronized CreateIteratorResult createIterator(Bucket bucket, FieldSet fieldSet, Selection selection, PersistenceProvider.IncludedVersions versions) {
        nextIteratorId++;

        List<Long> timestamps = new ArrayList<Long>();
        if (selection.getTimestampSubset() == null) {
            for (DocEntry e : bucketContents.get(bucket.getBucketId()).entries) {
                timestamps.add(e.getTimestamp());
            }
        } else {
            timestamps.addAll(selection.getTimestampSubset());
            // Explicitly specifying a timestamp subset implies that any version may
            // be returned. This is essential for merging to work correctly.
            versions = IncludedVersions.ALL_VERSIONS;
        }

        iteratorContexts.put(nextIteratorId - 1, new IteratorContext(bucket, fieldSet, selection, timestamps, versions));
        return new CreateIteratorResult(nextIteratorId - 1);
    }

    @Override
    public synchronized IterateResult iterate(long iteratorId, long maxByteSize) {
        IteratorContext context = iteratorContexts.get(iteratorId);

        if (context == null) {
            return new IterateResult(Result.ErrorType.PERMANENT_ERROR, "Iterator id not found");
        }

        ArrayList<DocEntry> entries = new ArrayList<DocEntry>();
        for (DocEntry e : bucketContents.get(context.getBucket().getBucketId()).entries) {
            if (maxByteSize < 0) {
                return new IterateResult(entries, false);
            }

            if (context.getTimestamps().contains(e.getTimestamp())) {
                context.getTimestamps().remove(e.getTimestamp());
            } else {
                continue;
            }

            if (e.getType() == DocEntry.Type.PUT_ENTRY) {

                if (context.getSelection() != null && !context.getSelection().match(e.getDocument(), e.getTimestamp())) {
                    continue;
                }
                entries.add(e);
                maxByteSize -= e.getDocument().getSerializedSize();
            } else if (context.getIncludedVersions() == PersistenceProvider.IncludedVersions.NEWEST_DOCUMENT_OR_REMOVE
                    || context.getIncludedVersions() == PersistenceProvider.IncludedVersions.ALL_VERSIONS)
            {

                if (context.getSelection() != null && !context.getSelection().match(e.getTimestamp())) {
                    continue;
                }

                entries.add(e);
                maxByteSize -= e.getDocumentId().toString().length();
            }
        }

        return new IterateResult(entries, true);
    }

    @Override
    public synchronized Result destroyIterator(long iteratorId) {
        iteratorContexts.remove(iteratorId);
        return new Result();
    }

    @Override
    public synchronized Result createBucket(Bucket bucket) {
        bucketContents.put(bucket.getBucketId(), new BucketContents());
        return new Result();
    }

    @Override
    public synchronized Result deleteBucket(Bucket bucket) {
        bucketContents.remove(bucket.getBucketId());
        return new Result();
    }

    private void mergeExistingBucketContentsIntoNew(BucketContents newC, BucketContents oldC) {
        if (oldC == null) {
            return;
        }
        Set<Long> newTimestamps = new HashSet<Long>();
        for (DocEntry entry : newC.entries) {
            newTimestamps.add(entry.getTimestamp());
        }
        // Don't overwrite new entries with old ones
        for (DocEntry oldEntry : oldC.entries) {
            if (newTimestamps.contains(oldEntry.getTimestamp())) {
                continue;
            }
            newC.entries.add(oldEntry);
        }
    }

    @Override
    public synchronized Result split(Bucket source, Bucket target1, Bucket target2) {
        BucketContents sourceContent = bucketContents.get(source.getBucketId());
        BucketContents existingTarget1 = bucketContents.get(target1.getBucketId());
        BucketContents existingTarget2 = bucketContents.get(target2.getBucketId());

        Pair<BucketContents, BucketContents> contents
                = sourceContent.split(target1.getBucketId(), target2.getBucketId());

        bucketContents.remove(source.getBucketId());
        mergeExistingBucketContentsIntoNew(contents.getFirst(), existingTarget1);
        mergeExistingBucketContentsIntoNew(contents.getSecond(), existingTarget2);

        BucketInfo.ActiveState targetActiveState
                = (sourceContent.getBucketInfo().isActive()
                   ? BucketInfo.ActiveState.ACTIVE
                   : BucketInfo.ActiveState.NOT_ACTIVE);
        contents.getFirst().setActiveState(targetActiveState);
        contents.getSecond().setActiveState(targetActiveState);

        bucketContents.put(target1.getBucketId(), contents.getFirst());
        bucketContents.put(target2.getBucketId(), contents.getSecond());

        return new Result();
    }

    @Override
    public synchronized Result join(Bucket source1, Bucket source2, Bucket target) {
        BucketInfo.ActiveState activeState = BucketInfo.ActiveState.NOT_ACTIVE;
        BucketContents targetExisting = bucketContents.get(target.getBucketId());
        BucketContents sourceExisting1 = bucketContents.get(source1.getBucketId());
        BucketContents sourceExisting2 = null;
        boolean singleBucketJoin = source2.getBucketId().equals(source1.getBucketId());
        if (!singleBucketJoin) {
            sourceExisting2 = bucketContents.get(source2.getBucketId());
        }

        if (sourceExisting1 != null && sourceExisting1.isActive()) {
            activeState = BucketInfo.ActiveState.ACTIVE;
        }
        if (sourceExisting2 != null && sourceExisting2.isActive()) {
            activeState = BucketInfo.ActiveState.ACTIVE;
        }

        BucketContents contents = new BucketContents(sourceExisting1, sourceExisting2);
        bucketContents.remove(source1.getBucketId());
        if (sourceExisting2 != null) {
            bucketContents.remove(source2.getBucketId());
        }
        mergeExistingBucketContentsIntoNew(contents, targetExisting);
        contents.setActiveState(activeState);
        bucketContents.put(target.getBucketId(), contents);
        return new Result();
    }

    @Override
    public synchronized Result setActiveState(Bucket bucket, BucketInfo.ActiveState active) {
        bucketContents.get(bucket.getBucketId()).setActiveState(active);
        return new Result();
    }
}
