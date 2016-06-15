// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.persistence.spi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.fieldset.FieldSet;
import com.yahoo.persistence.spi.result.*;

/**
 * <p>
 * This interface is the basis for a persistence provider in Vespa.
 * A persistence provider is used by Vespa Storage to provide an elastic stateful system.
 * </p>
 * <p>
 * The Vespa distribution mechanisms are based on distributing "buckets"
 * between the nodes in the system. A bucket is an abstract concept that
 * groups a set of documents. The persistence provider can choose freely
 * how to implement a bucket, but it needs to be able to access a bucket as
 * a unit. The placement of these units is controlled by the distributors.
 * </p>
 * <p>
 * A persistence provider may support multiple "partitions". One example of
 * a partition is a physical disk, but the exact meaning of "partitions"
 * is left to the provider. It must be able to report to the service layer though.
 * </p>
 * <p>
 * All operations return a Result object. The base Result class only encapsulates
 * potential errors, which can be <i>transient</i>, <i>permanent</i> or <i>fatal</i>.
 * Transient errors are errors where it's conceivable that retrying the operation
 * would lead to success, either on this data copy or on others. Permanent errors
 * are errors where the request itself is faulty. Fatal errors are transient errors
 * that have uncovered a problem with this instance of the provider (such as a failing disk),
 * and where the provider wants the process to be shut down.
 * </p>
 * <p>
 * All write operations have a timestamp. This timestamp is generated
 * by the distributor, and is guaranteed to be unique for the bucket we're
 * writing to. A persistence provider is required to store "entries" for each of
 * these operations, and associate the timestamp with that entry.
 * Iteration code can retrieve these entries, including entries
 * for remove operations. The provider is not required to keep any history beyond
 * the last operation that was performed on a given document.
 * </p>
 * <p>
 * The contract for all write operations is that after returning from the function,
 * provider read methods (get, iterate) should reflect the modified state.
 * </p>
 */
public interface PersistenceProvider
{
    /**
     * The different types of entries that can be returned
     * from an iterator.
     */
    public enum IncludedVersions {
        NEWEST_DOCUMENT_ONLY,
        NEWEST_DOCUMENT_OR_REMOVE,
        ALL_VERSIONS
    }

    /**
     * The different kinds of maintenance we can do.
     * LOW maintenance may be run more often than HIGH.
     */
    public enum MaintenanceLevel {
        LOW,
        HIGH
    }

    /**
     * Initializes the persistence provider. This function is called exactly once when
     * the persistence provider starts. If any error is returned here, the service layer
     * will shut down.
     */
    Result initialize();

    /**
     * Returns a list of the partitions available,
     * and which are up and down.
     */
    PartitionStateListResult getPartitionStates();

    /**
     * Return list of buckets that provider has stored on the given partition.
     */
    BucketIdListResult listBuckets(short partition);

    /**
     * Updates the persistence provider with the last cluster state.
     * Only cluster states that are relevant for the provider are supplied (changes
     * that relate to the distributor will not cause an update here).
     */
    Result setClusterState(ClusterState state);

    /**
     * Sets the bucket state to active or inactive. After this returns,
     * other buckets may be deactivated, so the node must be able to serve
     * the data from its secondary index or get reduced coverage.
     */
    Result setActiveState(Bucket bucket, BucketInfo.ActiveState active);

    /**
     * If the bucket doesn't exist, return empty bucket info.
     */
    BucketInfoResult getBucketInfo(Bucket bucket);

    /**
     * Stores the given document.
     *
     * @param timestamp The timestamp for the new bucket entry.
     */
    Result put(Bucket bucket, long timestamp, Document doc);

    /**
     * <p>
     * Removes the document referenced by the document id.
     * It is strongly recommended to keep entries for the removes for
     * some period of time. For recovery to work properly, a node that
     * has been down for a longer period of time than that should be totally
     * erased. If not, documents that have been removed but have documents
     * on nodes that have been down will be reinserted.
     * </p>
     * <p>
     * Postconditions:
     * A successful invocation of this function must add the remove to the
     * bucket regardless of whether the document existed. More specifically,
     * iterating over the bucket while including removes after this call
     * shall yield a remove-entry at the given timestamp for the given
     * document identifier as part of its result set. The remove entry
     * shall be added even if there exist removes for the same document id
     * at other timestamps in the bucket.
     * </p>
     * <p>
     * Also, if the given timestamp is higher to or equal than any
     * existing put entry, those entries should not be returned in subsequent
     * get calls. If the timestamp is lower than an existing put entry,
     * those entries should still be available.
     * </p>
     * @param timestamp The timestamp for the new bucket entry.
     * @param id The ID to remove
     */
    RemoveResult remove(Bucket bucket, long timestamp, DocumentId id);
    /**
     * <p>
     * See remove()
     * </p>
     * <p>
     * Used for external remove operations. removeIfFound() has no extra
     * postconditions than remove, but it may choose to <i>not</i> include
     * a remove entry if there didn't already exist a put entry for the given
     * entry. It is recommended, but not required, to not insert entries in this
     * case, though if remove entries are considered critical it might be better
     * to insert them in both cases.
     * </p>
     * @param timestamp The timestamp for the new bucket entry.
     * @param id The ID to remove
     */
    RemoveResult removeIfFound(Bucket bucket, long timestamp, DocumentId id);

    /**
     * Removes the entry with the given timestamp. This is usually used to revert
     * previously performed operations. This operation should be
     * successful even if there doesn't exist such an entry.
     */
    Result removeEntry(Bucket bucket, long timestampToRemove);

    /**
     * Partially modifies a document referenced by the document update.
     *
     * @param timestamp The timestamp to use for the new update entry.
     * @param update The document update to apply to the stored document.
     */
    UpdateResult update(Bucket bucket, long timestamp, DocumentUpdate update);

    /**
     * <p>
     * For providers that store data persistently on disk, the contract of
     * flush is that data has been stored persistently so that if the node should
     * restart, the data will be available.
     * </p>
     * <p>
     * The service layer may choose to batch certain commands. This means
     * that the service layer will lock the bucket only once, then perform several
     * commands, and finally get the bucket info from the bucket, and then flush it.
     * This can be used to improve performance by caching the modifications, and
     * persisting them to disk only when flush is called. The service layer guarantees
     * that after one of these operations, flush() is called, regardless of whether
     * the operation succeeded or not, before another bucket is processed in the same
     * worker thread. The following operations can be batched and have the guarantees
     * above:
     * - put
     * - get
     * - remove
     * - removeIfFound
     * - update
     * - removeEntry
     * </p>
     */
    Result flush(Bucket bucket);

    /**
     * Retrieves the latest version of the document specified by the
     * document id. If no versions were found, or the document was removed,
     * the result should be successful, but contain no document (see GetResult).
     *
     * @param fieldSet A set of fields that should be retrieved.
     * @param id The document id to retrieve.
     */
    GetResult get(Bucket bucket, FieldSet fieldSet, DocumentId id);

    /**
     * Create an iterator for a given bucket and selection criteria, returning
     * a unique, non-zero iterator identifier that can be used by the caller as
     * an argument to iterate and destroyIterator.
     *
     * Each successful invocation of createIterator shall be paired with
     * a later invocation of destroyIterator by the caller to ensure
     * resources are freed up. NOTE: this may not apply in a shutdown
     * situation due to service layer communication channels closing down.
     *
     * It is assumed that a successful invocation of this function will result
     * in some state being established in the persistence provider, holding
     * the information required to match iterator ids up to their current
     * iteration progress and selection criteria. destroyIterator will NOT
     * be called when createIterator returns an error.
     *
     * @param selection Selection criteria used to limit the subset of
     *   the bucket's documents that will be returned by the iterator. The
     *   provider implementation may use these criteria to optimize its
     *   operation as it sees fit, as long as doing so does not violate
     *   selection correctness.
     * @return A process-globally unique iterator identifier iff the result
     *   is successful and internal state has been created, otherwise an
     *   error. Identifier must be non-zero, as zero is used internally to
     *   signify an invalid iterator ID.
     */
    CreateIteratorResult createIterator(Bucket bucket,
                                        FieldSet fieldSet,
                                        Selection selection,
                                        IncludedVersions versions);

    /**
     * Iterate over a bucket's document space using a valid iterator id
     * received from createIterator. Each invocation of iterate upon an
     * iterator that has not yet fully exhausted its document space shall
     * return a minimum of 1 document entry per IterateResult to ensure progress.
     * An implementation shall limit the result set per invocation to document
     * entries whose combined in-memory/serialized size is a "soft" maximum of
     * maxByteSize. More specifically, the sum of getSize() over all returned
     * DocEntry instances should be &lt;= (maxByteSize + the size of the last
     * document in the result set). This special case allows for limiting the
     * result set both by observing "before the fact" that the next potential
     * document to include would exceed the max size and by observing "after
     * the fact" that the document that was just added caused the max size to
     * be exceeded.
     * However, if a document exceeds maxByteSize and not including it implies
     * the result set would be empty, it must be included in the result anyway
     * in order to not violate the progress requirement.
     *
     * The caller shall not make any assumptions on whether or not documents
     * that arrive to--or are removed from--the bucket in the time between
     * separate invocations of iterate for the same iterator id will show up
     * in the results, assuming that these documents do not violate the
     * selection criteria. This means that there is no requirement for
     * maintaining a "snapshot" view of the bucket's state as it existed upon
     * the initial createIterator call. Neither shall the caller make any
     * assumptions on the ordering of the returned documents.
     *
     * The IterateResult shall--for each document entry that matches the
     * selection criteria and falls within the maxByteSize limit mentioned
     * above--return the following information in its result:
     *
     *  -- For non-removed entries: A DocEntry where getDocumentOperation() will
     *     return a valid DocumentPut instance and getSize() will return the
     *     serialized size of the document.
     *  -- For removed entries: A DocEntry where getDocumentId() will
     *     return a valid document identifier. Remove entries shall not
     *     contain document instances.
     *  -- For meta entries: A DocEntry that shall not contain a document
     *     instance nor should it include a document id instance (if
     *     included, would be ignored by the service layer in any context
     *     where metadata-only is requested).
     *
     * The service layer shall guarantee that no two invocations of iterate
     * will happen simultaneously/concurrently for the same iterator id.
     *
     * Upon a successful invocation of iterate, the persistence provider shall
     * update its internal state to account for the progress made so that new
     * invocations will cover a new subset of the document space. When an
     * IterateResult contains the final documents for the iteration, i.e. the
     * iterator has reached its end, setCompleted() must be set on the result
     * to indicate this to the caller. Calling iterate on an already completed
     * iterator must only set this flag on the result and return without any
     * documents.
     *
     * @param iteratorId An iterator ID returned by a previous call to createIterator
     * @param maxByteSize An indication of the maximum number of bytes that should be returned.
     */
    IterateResult iterate(long iteratorId, long maxByteSize);

    /**
     * <p>
     * Destroys the iterator specified by the given id.
     * </p>
     * <p>
     * IMPORTANT: this method has different invocation semantics than
     * the other provider methods! It may be called from the context of
     * ANY service layer thread, NOT just from the thread in which
     * createIterator was invoked! The reason for this is because internal
     * iterator destroy messages aren't mapped to partition threads in the
     * way other messages are due to their need for guaranteed execution.
     * </p>
     * <p>
     * This in turn implies that iterator states must be shared between
     * partitions (and thus protected against cross-partition concurrent
     * access).
     * </p>
     * @param iteratorId The iterator id previously returned by createIterator.
     */
    Result destroyIterator(long iteratorId);

    /**
     * Tells the provider that the given bucket has been created in the
     * service layer. There is no requirement to do anything here.
     */
    Result createBucket(Bucket bucket);

    /**
     * Deletes the given bucket and all entries contained in that bucket.
     * After this operation has succeeded, a restart of the provider should
     * not yield the bucket in getBucketList().
     */
    Result deleteBucket(Bucket bucket);

    /**
     * This function is called continuously by the service layer. It allows
     * the provider to signify whether it has done any out-of-band changes to
     * buckets that need to be recognized by the rest of the system. The service
     * layer will proceed to call getBucketInfo() on each of the returned buckets.
     * After a call to getModifiedBuckets(), the provider should clear it's list
     * of modified buckets, so that the next call does not return the same buckets.
     */
    BucketIdListResult getModifiedBuckets();

    /**
     * Allows the provider to do periodic maintenance and verification.
     *
     * @param level The level of maintenance to do. LOW maintenance is scheduled more
     * often than HIGH maintenance, so should be cheaper.
     */
    Result maintain(Bucket bucket, MaintenanceLevel level);

    /**
     * <p>
     * Splits the source bucket into the two target buckets.
     * After the split, all documents belonging to target1 should be
     * in that bucket, and all documents belonging to target2 should be
     * there. The information in SplitResult should reflect
     * this.
     * </p>
     * <p>
     * Before calling this function, the service layer will iterate the bucket
     * to figure out which buckets the source should be split into. This may
     * result in splitting more than one bucket bit at a time.
     * </p>
     */
    Result split(Bucket source, Bucket target1, Bucket target2);

    /**
     * Joins two buckets into one. After the join, all documents from
     * source1 and source2 should be stored in the target bucket.
     */
    Result join(Bucket source1, Bucket source2, Bucket target);

    /**
     * Moves a bucket from one partition to another.
     *
     * @param partitionId The partition to move to.
     */
    Result move(Bucket bucket, short partitionId);
}
