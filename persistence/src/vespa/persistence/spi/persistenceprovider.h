// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucket.h"
#include "bucketinfo.h"
#include "context.h"
#include "docentry.h"
#include "documentselection.h"
#include "partitionstate.h"
#include "result.h"
#include "selection.h"
#include "clusterstate.h"

namespace document {
    class FieldSet;
}

namespace storage::spi {

/**
 * This interface is the basis for a persistence provider in Vespa.  A
 * persistence provider is used by Vespa Storage to provide an elastic stateful
 * system.
 * <p/>
 * The Vespa distribution mechanisms are based on distributing "buckets"
 * between the nodes in the system. A bucket is an abstract concept that
 * groups a set of documents. The persistence provider can choose freely
 * how to implement a bucket, but it needs to be able to access a bucket as
 * a unit. The placement of these units is controlled by the distributors.
 * <p/>
 * A persistence provider may support multiple "partitions". One example of
 * a partition is a physical disk, but the exact meaning of "partitions"
 * is left to the provider. It must be able to report to the service layer
 * though.
 * <p/>
 * All operations return a Result object. The base Result class only
 * encapsulates potential errors, which can be <i>transient</i>,
 * <i>permanent</i> or <i>fatal</i>. Transient errors are errors where it's
 * conceivable that retrying the operation would lead to success, either on
 * this data copy or on others. Permanent errors are errors where the request
 * itself is faulty. Fatal errors are transient errors that have uncovered a
 * problem with this instance of the provider (such as a failing disk), and
 * where the provider wants the process to be shut down.
 * <p/>
 * All write operations have a timestamp. This timestamp is generated
 * by the distributor, and is guaranteed to be unique for the bucket we're
 * writing to. A persistence provider is required to store "entries" for each of
 * these operations, and associate the timestamp with that entry.
 * Iteration code can retrieve these entries, including entries for remove
 * operations. The provider is not required to keep any history beyond the last
 * operation that was performed on a given document.
 * <p/>
 * The contract for all write operations is that after returning from the
 * function, provider read methods (get, iterate) should reflect the modified
 * state.
 * <p/>
 */
struct PersistenceProvider
{
    typedef std::unique_ptr<PersistenceProvider> UP;
    using BucketSpace = document::BucketSpace;

    virtual ~PersistenceProvider();

    /**
     * Initializes the persistence provider. This function is called exactly
     * once when the persistence provider starts. If any error is returned
     * here, the service layer will shut down.
     *
     * Also note that this function is called in the application main thread,
     * and any time spent in initialize will be while service layer node is
     * considered down and unavailable.
     */
    virtual Result initialize() = 0;

    /**
     * Returns a list of the partitions available, and which are up and down.
     * Currently called once on startup. Partitions are not allowed to change
     * runtime.
     */
    virtual PartitionStateListResult getPartitionStates() const = 0;

    /**
     * Return list of buckets that provider has stored on the given partition.
     * Typically called once per partition on startup.
     */
    virtual BucketIdListResult listBuckets(BucketSpace bucketSpace, PartitionId) const = 0;

    /**
     * Updates the persistence provider with the last cluster state.
     * Only cluster states that are assumed relevant for the provider are
     * supplied (changes that relate to the distributor will not cause an
     * update here).
     */
    virtual Result setClusterState(BucketSpace bucketSpace, const ClusterState&) = 0;

    /**
     * Sets the bucket state to active or inactive. After this returns,
     * other buckets may be deactivated, so the node must be able to serve
     * the data from its secondary index or get reduced coverage.
     */
    virtual Result setActiveState(const Bucket&, BucketInfo::ActiveState) = 0;

    /**
     * Retrieve metadata for a bucket, previously returned in listBuckets(),
     * or created through SPI explicitly (createBucket) or implicitly
     * (split, join).
     */
    virtual BucketInfoResult getBucketInfo(const Bucket&) const = 0;

    /**
     * Store the given document at the given microsecond time.
     */
    virtual Result put(const Bucket&, Timestamp, const DocumentSP&, Context&) = 0;

    /**
     * This remove function assumes that there exist something to be removed.
     * The data to be removed may not exist on this node though, so all remove
     * entries inserted with this function should be kept for some time in
     * order for data not to be reintroduced from other nodes that may be
     * temporarily down. To avoid reintroduction of removed documents, nodes
     * that has been down longer than removes are kept, should have their data
     * cleared before being reintroduced into the cluster.
     * <p/>
     * You may choose to ignore the remove if the document already exist (or has
     * a remove entry) at a newer timestamp than the given one.
     * <p/>
     * In the special case where the document exist at the same timestamp
     * given, this entry should be turned into a remove entry. This is
     * functionality needed in order for the cluster to be able to remove a
     * subset of data not known ahead of the remove request.
     *
     * Postconditions:
     * A successful invocation of this function shall cause a remove entry
     * for the given timestamp and document ID pair to be present in a
     * subsequent full iteration over the bucket if:
     *  - there did not already exist any entries for the document
     *  - OR: any existing entries are older than the remove's timestamp.
     * A provider capable of preserving historical
     * document entry information MAY choose to persist the remove even if
     * these conditions are not met, but this is not mandatory. All instances of
     * the provider in the cluster must operate deterministically in the same
     * manner to ensure that applying a set of timestamped operations will end
     * up with a consistent result across all the replica nodes.
     * <p/>
     * NOTE: "subsequent full iteration" in this context means an iteration
     * operation that happens within the period in which removes are to be kept
     * by the persistence provider and which is tagged to include removes and/or
     * all versions.
     * <p/>
     * NOTE: if the given timestamp is higher to or equal than any
     * existing put entry, those entries should not be returned in subsequent
     * get calls. If the timestamp is lower than an existing put entry,
     * those entries should still be available.
     * <p/>
     * EXAMPLE: A provider not supporting historical document entries is
     * still fully conformant if it maintains the following invariants:
     *   - a remove for a document that does not have any existing entries is
     *     always persisted.
     *   - a remove with an older timestamp than any existing entries for the
     *     given document identifier (puts and/or removes) is not persisted, but
     *     ignored.
     *   - a put or remove with a newer timestamp than all existing entries
     *     for the given document identifier is persisted, causing older
     *     entries to be effectively discarded.
     * For such a provider, iterating with removes and all versions should
     * semantically be the same thing and yield the same results.
     *
     * @param timestamp The timestamp for the new bucket entry.
     * @param id The ID to remove
     */
    virtual RemoveResult remove(const Bucket&, Timestamp timestamp, const DocumentId& id, Context&) = 0;
    /**
     * @see remove()
     * <p/>
     * Used for external remove operations. removeIfFound() works as remove(),
     * but you are not required to insert a remove entry if document does not
     * exist locally. This difference exist, such that users can't fill the
     * cluster up with remove entries by misspelling identifiers or repeatedly
     * resend removes. It is legal to still store a remove entry, but note that
     * you will then be prone to user patterns mentioned above to fill up your
     * buckets.
     * <p/>
     * @param timestamp The timestamp for the new bucket entry.
     * @param id The ID to remove
     */
    virtual RemoveResult removeIfFound(const Bucket&, Timestamp timestamp, const DocumentId& id, Context&) = 0;

    /**
     * Remove any trace of the entry with the given timestamp. (Be it a document
     * or a remove entry) This is usually used to revert previously performed
     * operations, in order to try best effort to not keep data we say we have
     * failed to insert. This operation should be successful even if there
     * doesn't exist such an entry.
     */
    virtual Result removeEntry(const Bucket&, Timestamp, Context&) = 0;

    /**
     * Partially modifies a document referenced by the document update.
     *
     * @param timestamp The timestamp to use for the new update entry.
     * @param update The document update to apply to the stored document.
     */
    virtual UpdateResult update(const Bucket&, Timestamp timestamp, const DocumentUpdateSP& update, Context&) = 0;

    /**
     * The service layer may choose to batch certain commands. This means that
     * the service layer will lock the bucket only once, then perform several
     * commands, and finally get the bucket info from the bucket, and then
     * flush it.  This can be used to improve performance by caching the
     * modifications, and persisting them to disk only when flush is called.
     * The service layer guarantees that after one of these operations, flush()
     * is called, regardless of whether the operation succeeded or not, before
     * another bucket is processed in the same worker thead. The following
     * operations can be batched and have the guarantees
     * above:
     * - put
     * - get
     * - remove (all versions)
     * - update
     * - revert
     * - join
     * <p/>
     * A provider may of course choose to not sync to disk at flush time either,
     * but then data may be more prone to being lost on node issues, and the
     * provider must figure out when to flush its cache itself.
     */
    virtual Result flush(const Bucket&, Context&) = 0;

    /**
     * Retrieves the latest version of the document specified by the
     * document id. If no versions were found, or the document was removed,
     * the result should be successful, but contain no document (see GetResult).
     *
     * @param fieldSet A set of fields that should be retrieved.
     * @param id The document id to retrieve.
     */
    virtual GetResult get(const Bucket&, const document::FieldSet& fieldSet, const DocumentId& id, Context&) const = 0;

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
    virtual CreateIteratorResult createIterator(
            const Bucket&,
            const document::FieldSet& fieldSet,
            const Selection& selection, //TODO: Make AST
            IncludedVersions versions,
            Context&) = 0;

    /**
     * Iterate over a bucket's document space using a valid iterator id
     * received from createIterator. Each invocation of iterate upon an
     * iterator that has not yet fully exhausted its document space shall
     * return a minimum of 1 document entry per IterateResult to ensure
     * progress. An implementation shall limit the result set per invocation
     * to document entries whose combined in-memory/serialized size is a "soft"
     * maximum of maxByteSize. More specifically, the sum of getSize() over all
     * returned DocEntry instances should be <= (maxByteSize + the size of the
     * last document in the result set). This special case allows for limiting
     * the result set both by observing "before the fact" that the next
     * potential document to include would exceed the max size and by observing
     * "after the fact" that the document that was just added caused the max
     * size to be exceeded.  However, if a document exceeds maxByteSize and not
     * including it implies the result set would be empty, it must be included
     * in the result anyway in order to not violate the progress requirement.
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
     *  -- For non-removed entries: A DocEntry where getDocument() will
     *     return a valid document instance and getSize() will return the
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
     * @param id An iterator ID returned by a previous call to createIterator
     * @param maxByteSize An indication of the maximum number of bytes that
     * should be returned.
     */
    virtual IterateResult iterate(IteratorId id, uint64_t maxByteSize, Context&) const = 0;

    /**
     * Destroys the iterator specified by the given id.
     * <p/>
     * IMPORTANT: this method has different invocation semantics than
     * the other provider methods! It may be called from the context of
     * ANY service layer thread, NOT just from the thread in which
     * createIterator was invoked! The reason for this is because internal
     * iterator destroy messages aren't mapped to partition threads in the
     * way other messages are due to their need for guaranteed execution.
     * <p/>
     * This in turn implies that iterator states must be shared between
     * partitions (and thus protected against cross-partition concurrent
     * access).
     * <p/>
     * @param id The iterator id previously returned by createIterator.
     */
    virtual Result destroyIterator(IteratorId id, Context&) = 0;

    /**
     * Tells the provider that the given bucket has been created in the
     * service layer. There is no requirement to do anything here.
     */
    virtual Result createBucket(const Bucket&, Context&) = 0;

    /**
     * Deletes the given bucket and all entries contained in that bucket.
     * After this operation has succeeded, a restart of the provider should
     * not yield the bucket in getBucketList().
     */
    virtual Result deleteBucket(const Bucket&, Context&) = 0;

    /**
     * This function is called continuously by the service layer. It allows the
     * provider to signify whether it has done any out-of-band changes to
     * buckets that need to be recognized by the rest of the system. The
     * service layer will proceed to call getBucketInfo() on each of the
     * returned buckets.  After a call to getModifiedBuckets(), the provider
     * should clear it's list of modified buckets, so that the next call does
     * not return the same buckets.
     */
    virtual BucketIdListResult getModifiedBuckets(BucketSpace bucketSpace) const = 0;

    /**
     * Allows the provider to do periodic maintenance and verification.
     *
     * @param level The level of maintenance to do. LOW maintenance is
     * scheduled more often than HIGH maintenance, allowing costly operations
     * to be run less.
     */
    virtual Result maintain(const Bucket&, MaintenanceLevel level) = 0;

    /**
     * Splits the source bucket into the two target buckets.
     * After the split, all documents belonging to target1 should be
     * in that bucket, and all documents belonging to target2 should be
     * there. The information in SplitResult should reflect
     * this.
     * <p/>
     * Before calling this function, the service layer will iterate the bucket
     * to figure out which buckets the source should be split into. This may
     * result in splitting more than one bucket bit at a time.
     * <p/>
     * In some cases, we might want to just up used bit count in bucket, as we
     * don't want to split far enough to split content in two. In these cases
     * target2 will specify invalid bucket 0 (with 0 used bits).
     */
    virtual Result split(const Bucket& source, const Bucket& target1, const Bucket& target2, Context&) = 0;

    /**
     * Joins two buckets into one. After the join, all documents from
     * source1 and source2 should be stored in the target bucket.
     */
    virtual Result join(const Bucket& source1, const Bucket& source2, const Bucket& target, Context&) = 0;

    /**
     * Moves a bucket from one partition to another.
     *
     * @param target The partition to move to. (From partition is in bucket)
     */
    virtual Result move(const Bucket&, PartitionId target, Context&) = 0;
};

}
