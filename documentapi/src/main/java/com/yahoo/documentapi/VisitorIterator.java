// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.select.BucketSelector;
import com.yahoo.document.select.parser.ParseException;
import java.util.logging.Level;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * <p>Enables transparent iteration of super/sub-buckets</p>
 *
 * <p>Thread safety: safe for threads to hold their own iterators (no shared state),
 * as long as they also hold the ProgressToken object associated with it. No two
 * VisitorIterator instances may share the same progress token instance at the
 * same time.
 * Concurrent access to a single VisitorIterator instance is not safe and must
 * be handled atomically by the caller.</p>
 *
 * @author vekterli
 */
public class VisitorIterator {

    private final ProgressToken progressToken;
    private final BucketSource bucketSource;
    private int distributionBitCount;

    private static final Logger log = Logger.getLogger(VisitorIterator.class.getName());

    public static class BucketProgress {
        private BucketId superbucket;
        private BucketId progress;

        public BucketProgress(BucketId superbucket, BucketId progress) {
            this.superbucket = superbucket;
            this.progress = progress;
        }

        public BucketId getProgress() {
            return progress;
        }

        public BucketId getSuperbucket() {
            return superbucket;
        }
    }

    /**
     * Provides an abstract interface to <code>VisitorIterator</code> for
     * how pending buckets are acquired, decoupling this from the iteration
     * itself.
     *
     * <em>Important</em>: it is the responsibility of the {@link BucketSource} implementation
     * to ensure that progress information is honored for (partially) finished buckets.
     * From the point of view of the iterator itself, it should not have to deal with
     * filtering away already finished buckets, as this is a detail best left to
     * bucket sources.
     */
    protected static interface BucketSource {
        public boolean hasNext();
        public boolean shouldYield();
        public boolean visitsAllBuckets();
        public BucketProgress getNext();
        public long getTotalBucketCount();
        public int getDistributionBitCount();
        public void setDistributionBitCount(int distributionBitCount,
                                            ProgressToken progress);
        public void update(BucketId superbucket, BucketId progress,
                           ProgressToken token);
    }

    /**
     * Provides a bucket source that encompasses the entire range available
     * through a given value of distribution bits
     */
    protected static class DistributionRangeBucketSource implements BucketSource {
        private boolean flushActive = false;
        private int distributionBitCount;
        // Wouldn't need this if this were a non-static class, but do it for
        // the sake of keeping things identical in Java and C++
        private ProgressToken progressToken;

        public DistributionRangeBucketSource(int distributionBitCount,
                                             ProgressToken progress) {
            progressToken = progress;

            // New progress token (could also be empty, in which this is a
            // no-op anyway)
            if (progressToken.getTotalBucketCount() == 0) {
                assert(progressToken.isEmpty()) : "inconsistent progress state";
                progressToken.setTotalBucketCount(1L << distributionBitCount);
                progressToken.setDistributionBitCount(distributionBitCount);
                progressToken.setBucketCursor(0);
                progressToken.setFinishedBucketCount(0);
                this.distributionBitCount = distributionBitCount;
            }
            else {
                this.distributionBitCount = progressToken.getDistributionBitCount();
                // Quick consistency check to ensure the user isn't trying to eg.
                // pass a progress token for an explicit document selection
                if (progressToken.getTotalBucketCount() != (1L << progressToken.getDistributionBitCount())) {
                    throw new IllegalArgumentException("Total bucket count in existing progress is not "
                            + "consistent with that of the current document selection");
                }
            }

            if (!progress.isFinished()) {
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "Importing unfinished progress token with " +
                            "bits: " + progressToken.getDistributionBitCount() +
                            ", active: " + progressToken.getActiveBucketCount() +
                            ", pending: " + progressToken.getPendingBucketCount() +
                            ", cursor: " + progressToken.getBucketCursor() +
                            ", finished: " + progressToken.getFinishedBucketCount() +
                            ", total: " + progressToken.getTotalBucketCount());
                }
                if (!progress.isEmpty()) {
                    // Lower all active to pending
                    if (progressToken.getActiveBucketCount() > 0) {
                        if (log.isLoggable(Level.FINE)) {
                            log.log(Level.FINE, "Progress token had active buckets upon range " +
                                    "construction. Setting these as pending");
                        }
                        progressToken.setAllBucketsToState(ProgressToken.BucketState.BUCKET_PENDING);
                    }
                    // Fixup for any buckets that were active when progress was written
                    // but are now pending and with wrong dist bits (used-bits). Buckets
                    // split here may very well be split/merged again if we set a new dist
                    // bit count, but that is the desired process
                    correctInconsistentPending(progressToken.getDistributionBitCount());
                    // Fixup for bucket cursor in case of bucket space downscaling
                    correctTruncatedBucketCursor();

                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Partial bucket space progress; continuing "+
                                "from position " + progressToken.getBucketCursor());
                    }
                }
                progressToken.setFinishedBucketCount(progressToken.getBucketCursor() -
                        progressToken.getPendingBucketCount());
            } else {
                assert(progressToken.getBucketCursor() == progressToken.getTotalBucketCount());
            }
            // Should be all fixed up and good to go
            progressToken.setInconsistentState(false);
        }

        protected boolean isLosslessResetPossible() {
            // #pending must be equal to cursor, i.e. all buckets ever fetched
            // must be located in the set of pending
            if (progressToken.getPendingBucketCount() != progressToken.getBucketCursor()) {
                return false;
            }
            // Check if all pending buckets have a progress of 0
            for (Map.Entry<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry> entry
                        : progressToken.getBuckets().entrySet()) {
                if (entry.getValue().getState() != ProgressToken.BucketState.BUCKET_PENDING) {
                    return false;
                }
                if (entry.getValue().getProgress().getId() != 0) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Ensure that a given <code>ProgressToken</code> instance only has
         * buckets pending that have a used-bits count of that of the
         * <code>targetDistCits</code>. This is done by splitting or merging
         * all inconsistent buckets until the desired state is reached.
         *
         * Time complexity is approx <i>O(4bn)</i> where <i>b</i> is the maximum
         * delta of bits to change anywhere in the set of pending and <i>n</i>
         * is the number of pending. This includes the time spent making shallow
         * map copies.
         *
         * @param targetDistBits The desired distribution bit count of the buckets
         */
        private void correctInconsistentPending(int targetDistBits) {
            boolean maybeInconsistent = true;
            long bucketsSplit = 0, bucketsMerged = 0;
            long pendingBefore = progressToken.getPendingBucketCount();
            ProgressToken p = progressToken;

            // Optimization: before doing any splitting/merging at all, we check
            // to see if we can't simply just reset the entire internal state
            // with the new distribution bit count. This ensures that if we go
            // from eg. 1 bit to 20 bits, we won't have to perform a grueling
            // half a million splits to cover the same bucket space as that 1
            // single-bit bucket once did
            if (isLosslessResetPossible()) {
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "At start of bucket space and all " +
                            "buckets have no progress; doing a lossless reset " +
                            "instead of splitting/merging");
                }
                assert(p.getActiveBucketCount() == 0);
                p.clearAllBuckets();
                p.setBucketCursor(0);
                return;
            }

            while (maybeInconsistent) {
                BucketId lastMergedBucket = null;
                maybeInconsistent = false;
                // Make a shallow working copy of the bucket map. BucketKeyWrapper
                // keys are considered immutable, and should thus not be at risk
                // for being changed during the inner loop
                // Do separate passes for splitting and merging just to make
                // absolutely sure that the two ops won't step on each others'
                // toes. This isn't wildly efficient, but the data sets in question
                // are presumed to be low in size and this is presumed to be a very
                // infrequent operation
                TreeMap<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry> buckets
                        = new TreeMap<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry>(p.getBuckets());
                for (Map.Entry<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry> entry
                        : buckets.entrySet()) {
                    assert(entry.getValue().getState() == ProgressToken.BucketState.BUCKET_PENDING);
                    BucketId pending = new BucketId(ProgressToken.keyToBucketId(entry.getKey().getKey()));
                    if (pending.getUsedBits() < targetDistBits) {
                        if (pending.getUsedBits() + 1 < targetDistBits) {
                            maybeInconsistent = true; // Do another pass
                        }
                        p.splitPendingBucket(pending);
                        ++bucketsSplit;
                    }
                }

                // Make new map copy with potentially split buckets
                buckets = new TreeMap<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry>(p.getBuckets());
                for (Map.Entry<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry> entry
                        : buckets.entrySet()) {
                    assert(entry.getValue().getState() == ProgressToken.BucketState.BUCKET_PENDING);
                    BucketId pending = new BucketId(ProgressToken.keyToBucketId(entry.getKey().getKey()));
                    if (pending.getUsedBits() > targetDistBits) {
                        // If this is the right sibling of an already merged left sibling,
                        // it's already been merged away, so we should skip it
                        if (lastMergedBucket != null) {
                            BucketId rightCheck = new BucketId(lastMergedBucket.getUsedBits(),
                                    lastMergedBucket.getId() | (1L << (lastMergedBucket.getUsedBits() - 1)));
                            if (pending.equals(rightCheck)) {
                                if (log.isLoggable(Level.FINEST)) {
                                    log.log(Level.FINEST, "Skipped " + pending +
                                            ", as it was right sibling of " + lastMergedBucket);
                                }
                                continue;
                            }
                        }
                        if (pending.getUsedBits() - 1 > targetDistBits) {
                            maybeInconsistent = true; // Do another pass
                        }
                        p.mergePendingBucket(pending);
                        ++bucketsMerged;

                        lastMergedBucket = pending;
                    }
                }
            }
            if ((bucketsSplit > 0 || bucketsMerged > 0) && log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Existing progress' pending buckets had inconsistent " +
                        "distribution bits; performed " + bucketsSplit + " split ops and " +
                        bucketsMerged + " merge ops. Pending: " + pendingBefore + " -> " +
                        p.getPendingBucketCount());
            }
        }

        private void correctTruncatedBucketCursor() {
            // We've truncated the bucket cursor, but in doing so we might
            // have moved back beyond where there are pending buckets. Consider
            // having a cursor value of 3 at 31 bits and then moving to 11 bits.
            // With 1 pending we'll normally reach a cursor of 0, even though it
            // should be 1
            for (ProgressToken.BucketKeyWrapper bucketKey
                    : progressToken.getBuckets().keySet()) {
                BucketId bid = bucketKey.toBucketId();
                long idx = bucketKey.getKey() >>> (64 - bid.getUsedBits());
                if (bid.getUsedBits() == distributionBitCount
                        && idx >= progressToken.getBucketCursor()) {
                    progressToken.setBucketCursor(idx + 1);
                }
            }
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "New range bucket cursor is " +
                        progressToken.getBucketCursor());
            }
        }

        public boolean hasNext() {
            return progressToken.getBucketCursor() < (1L << distributionBitCount);
        }

        public boolean shouldYield() {
            // If we need to flush all active buckets, stall the iteration until
            // this has been done
            return flushActive;
        }

        public boolean visitsAllBuckets() {
            return true;
        }

        public long getTotalBucketCount() {
            return 1L << distributionBitCount;
        }

        public BucketProgress getNext() {
            assert(hasNext()) : "getNext() called with hasNext() == false";
            long currentPosition = progressToken.getBucketCursor();
            long key = ProgressToken.makeNthBucketKey(currentPosition, distributionBitCount);
            ++currentPosition;
            progressToken.setBucketCursor(currentPosition);
            return new BucketProgress(
                    new BucketId(ProgressToken.keyToBucketId(key)),
                    new BucketId());
        }

        public int getDistributionBitCount() {
            return distributionBitCount;
        }

        public void setDistributionBitCount(int distributionBitCount,
                                            ProgressToken progress)
        {
            this.distributionBitCount = distributionBitCount;

            // There might be a case where we're waiting for active buckets
            // already when a new distribution bit change comes in. If so,
            // don't do anything at all yet with the set of pending
            if (progressToken.getActiveBucketCount() > 0) {
                flushActive = true;
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "Holding off new/pending buckets and consistency " +
                            "correction until all " + progress.getActiveBucketCount() +
                            " active buckets have been updated");
                }
                progressToken.setInconsistentState(true);
            } else {
                // Only perform the actual distribution bit bucket ops if we've
                // got no pending buckets
                int delta = distributionBitCount - progressToken.getDistributionBitCount();

                // Must do this before setting the bucket cursor to allow
                // reset-checking to be performed
                correctInconsistentPending(distributionBitCount);
                if (delta > 0) {
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Increasing distribution bits for full bucket " +
                                "space range source from " + progressToken.getDistributionBitCount() + " to " +
                                distributionBitCount);
                    }
                    progressToken.setFinishedBucketCount(progressToken.getFinishedBucketCount() << delta);
                    // By n-doubling the position, the bucket key ordering ensures
                    // we go from eg. 3:0x02 to 4:0x02 to 5:02 etc.
                    progressToken.setBucketCursor(progressToken.getBucketCursor() << delta);
                } else if (delta < 0) {
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Decreasing distribution bits for full bucket " +
                                "space range source from " + progressToken.getDistributionBitCount() +
                                " to " + distributionBitCount + " bits");
                    }
                    // Scale down bucket space and cursor
                    progressToken.setBucketCursor(progressToken.getBucketCursor() >>> -delta);
                    progressToken.setFinishedBucketCount(progressToken.getFinishedBucketCount() >>> -delta);
                }

                progressToken.setTotalBucketCount(1L << distributionBitCount);
                progressToken.setDistributionBitCount(distributionBitCount);

                correctTruncatedBucketCursor();
                progressToken.setInconsistentState(false);
            }
        }

        public void update(BucketId superbucket, BucketId progress,
                           ProgressToken token) {
            progressToken.updateProgress(superbucket, progress);

            if (superbucket.getUsedBits() != distributionBitCount) {
                if (!progress.equals(ProgressToken.FINISHED_BUCKET)) {
                    // We should now always flush active buckets before doing a
                    // consistency fix. This simplifies things greatly
                    assert(flushActive);
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Received non-finished bucket " +
                                superbucket + " with wrong distribution bit count (" +
                                superbucket.getUsedBits() + "). Waiting to correct " +
                                "until all active are done");
                    }
                } else {
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "Received finished bucket " +
                                superbucket + " with wrong distribution bit count (" +
                                superbucket.getUsedBits() + "). Waiting to correct " +
                                "until all active are done");
                    }
                }
            }

            if (progressToken.getActiveBucketCount() == 0) {
                if (flushActive) {
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "All active buckets flushed, " +
                                "correcting progress token and continuing normal operation");
                    }
                    // Trigger the actual bucket state change this time
                    setDistributionBitCount(distributionBitCount, progressToken);
                    assert(progressToken.getDistributionBitCount() == distributionBitCount);
                }
                flushActive = false;
                // Update #finished since we might have had inconsistent active
                // buckets that have prevent us from getting a correct value. At
                // this point, however, all pending buckets should presumably be
                // at the same, correct dist bit count, so we can safely compute
                // a new count
                // TODO: ensure this is consistent
                if (progressToken.getPendingBucketCount() <= progressToken.getBucketCursor()) {
                    progressToken.setFinishedBucketCount(progressToken.getBucketCursor() -
                            progressToken.getPendingBucketCount());
                }
            }
        }
    }

    /**
     * Provides an explicit set of bucket IDs to iterate over. Will immediately
     * set these as pending in the {@link ProgressToken}, as it is presumed this set is
     * rather small. Changing the distribution bit count for this source is
     * effectively a no-op, as explicit bucket IDs should not be implicitly
     * changed.
     */
    protected static class ExplicitBucketSource implements BucketSource {
        private int distributionBitCount;
        private long totalBucketCount = 0;

        public ExplicitBucketSource(Set<BucketId> superbuckets,
                                    int distributionBitCount,
                                    ProgressToken progress) {
            this.distributionBitCount = progress.getDistributionBitCount();
            this.totalBucketCount = superbuckets.size();

            // New progress token?
            if (progress.getTotalBucketCount() == 0) {
                progress.setTotalBucketCount(this.totalBucketCount);
                progress.setDistributionBitCount(distributionBitCount);
                this.distributionBitCount = distributionBitCount;
            }
            else {
                // Quick consistency check to ensure the user isn't trying to eg.
                // pass a progress token for another document selection
                if (progress.getTotalBucketCount() != totalBucketCount
                        || (progress.getFinishedBucketCount() + progress.getPendingBucketCount()
                            + progress.getActiveBucketCount() != totalBucketCount)) {
                    throw new IllegalArgumentException("Total bucket count in existing progress is not " +
                            "consistent with that of the current document selection");
                }
                if (progress.getBucketCursor() != 0) {
                    // Trying to use a range source progress file
                    throw new IllegalArgumentException("Cannot use given progress file with the "+
                            "current document selection");
                }
                this.distributionBitCount = progress.getDistributionBitCount();
            }

            if (progress.isFinished() || !progress.isEmpty()) return;

            for (BucketId id : superbuckets) {
                // Add all superbuckets with zero sub-bucket progress and pending
                progress.addBucket(id, new BucketId(), ProgressToken.BucketState.BUCKET_PENDING);
            }
        }

        public boolean hasNext() {
            return false;
        }

        public boolean shouldYield() {
            return false;
        }

        public boolean visitsAllBuckets() {
            return false;
        }

        public long getTotalBucketCount() {
            return totalBucketCount;
        }

        // All explicit buckets should have been placed in the progress
        // token during construction, so this method should never be called
        public BucketProgress getNext() {
            throw new IllegalStateException("getNext() called on ExplicitBucketSource");
        }

        public int getDistributionBitCount() {
            return distributionBitCount;
        }

        public void setDistributionBitCount(int distributionBitCount,
                                            ProgressToken progress)
        {
            // Setting distribution bits for explicit bucket source is essentially
            // a no-op, since its buckets already are fixed at 32 used bits.
            progress.setDistributionBitCount(distributionBitCount);
            this.distributionBitCount = distributionBitCount;
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Set distribution bit count to "
                        + distributionBitCount + " for explicit bucket source (no-op)");
            }
        }

        public void update(BucketId superbucket, BucketId progress,
                           ProgressToken token) {
            // Simply delegate to ProgressToken, as it maintains all progress state
            token.updateProgress(superbucket, progress);
        }
    }

    /**
     * @param bucketSource An instance of {@link BucketSource}, providing the working set for
     * the iterator
     * @param progressToken A {@link ProgressToken} instance, allowing the progress of
     * finished or partially finished buckets to be tracked
     *
     * @see BucketSource
     * @see ProgressToken
     */
    private VisitorIterator(ProgressToken progressToken,
                            BucketSource bucketSource) {
        assert(progressToken.getDistributionBitCount() == bucketSource.getDistributionBitCount())
                : "inconsistent distribution bit counts";
        this.distributionBitCount = progressToken.getDistributionBitCount();
        this.progressToken = progressToken;
        this.bucketSource = bucketSource;
    }


    /**
     * @return The pair [superbucket, progress] that specifies the next iterable
     * bucket. When a superbucket is initially returned, the pair is equal to
     * that of [superbucket, 0], as there has been no progress into its sub-buckets
     * yet (if they exist).
     *
     * Precondition: <code>hasNext() == true</code>
     */
    public BucketProgress getNext() {
        assert(progressToken.getDistributionBitCount() == bucketSource.getDistributionBitCount())
                : "inconsistent distribution bit counts for progress and source";
        assert(hasNext());
        // We prioritize returning buckets in the pending map over those
        // that may be in the bucket source, since we want to avoid growing
        // the map too much
        if (progressToken.hasPending()) {
            // Find first pending bucket in token
            TreeMap<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry> buckets = progressToken.getBuckets();
            ProgressToken.BucketEntry pending = null;
            BucketId superbucket = null;
            for (Map.Entry<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry> entry : buckets.entrySet()) {
                if (entry.getValue().getState() == ProgressToken.BucketState.BUCKET_PENDING) {
                    pending = entry.getValue();
                    superbucket = new BucketId(ProgressToken.keyToBucketId(entry.getKey().getKey()));
                    break;
                }
            }
            assert(pending != null) : "getNext() called with inconsistent state";

            // Set bucket to active, since it's not awaiting an update
            pending.setState(ProgressToken.BucketState.BUCKET_ACTIVE);

            progressToken.setActiveBucketCount(progressToken.getActiveBucketCount() + 1);
            progressToken.setPendingBucketCount(progressToken.getPendingBucketCount() - 1);

            return new BucketProgress(superbucket, pending.getProgress());
        } else {
            BucketProgress ret = bucketSource.getNext();
            progressToken.addBucket(ret.getSuperbucket(), ret.getProgress(),
                    ProgressToken.BucketState.BUCKET_ACTIVE);
            return ret;
        }
    }

    /**
     * <p>Check whether or not it is valid to call {@link #getNext()} with the current
     * iterator state.</p>
     *
     * <p>There exists a case wherein <code>hasNext</code> may return false before {@link #update} is
     * called, but true afterwards. This happens when the set of pending buckets is
     * empty, the bucket source is empty <em>but</em> the set of active buckets is
     * not. A future progress update on any of the buckets in the active set may
     * or may not make that bucket available to the pending set again.
     * This must be handled explicitly by the caller by checking {@link #isDone()}
     * and ensuring that {@link #update} is called before retrying <code>hasNext</code>.</p>
     *
     * <p>This method will also return false if the number of distribution bits have
     * changed and there are active buckets needing to be flushed before the
     * iterator will allow new buckets to be handed out.</p>
     *
     * @return Whether or not it is valid to call {@link #getNext()} with the current
     * iterator state.
     */
    public boolean hasNext() {
        return (progressToken.hasPending() || bucketSource.hasNext()) && !bucketSource.shouldYield();
    }

    /**
     * Check if the iterator is actually done
     *
     * @see #hasNext()
     *
     * @return <code>true</code> <em>iff</em> the bucket source is empty and
     * there are no pending or active buckets in the progress token.
     */
    public boolean isDone() {
        return !(hasNext() || progressToken.hasActive());
    }

    /**
     * <p>Tell the iterator that we've finished processing up to <i>and
     * including</i> <code>progress</code>. <code>progress</code> may be a sub-bucket <i>or</i>
     * the invalid 0-bucket (in case the caller fails to process the bucket and
     * must return it to the set of pending) <em>or</em> the special case <code>BucketId(Integer.MAX_VALUE)</code>,
     * the latter indicating to the iterator that traversal is complete for
     * <code>superbucket</code>'s tree. The null bucket should only be used if no
     * non-null updates have yet been given for the superbucket.</p>
     *
     * <p>It is a requirement that each superbucket returned by {@link #getNext()} must
     * eventually result in 1-n update operations, where the last update operation
     * has the special progress==super case.</p>
     *
     * <p>If the document selection used to create the iterator is unknown and there
     * were active buckets at the time of a distribution bit state change, such
     * a bucket passed to <code>update()</code> will be in an inconsistent state
     * with regards to the number of bits it uses. For unfinished buckets, this
     * is handled by splitting or merging it until it's consistent, depending on
     * whether or not it had a lower or higher distribution bit count than that of
     * the current system state. For finished buckets of a lower dist bit count,
     * the amount of finished buckets in the ProgressToken is adjusted upwards
     * to compensate for the fact that a bucket using fewer distribution bits
     * actually covers more of the bucket space than the ones that are currently
     * in use. For finished buckets of a higher dist bit count, the number of
     * finished buckets is <em>not</em> increased at that point in time, since
     * such a bucket doesn't actually cover an entire bucket with the current state.</p>
     *
     * <p>All this is done automatically and transparently to the caller once all
     * active buckets have been updated.</p>
     *
     * @param superbucket A valid bucket ID that has been retrieved earlier through
     * {@link #getNext()}
     * @param progress A bucket logically contained within <code>super</code>. Subsequent
     * updates for the same superbucket must have <code>progress</code> be in an increasing
     * order, where order is defined as the in-order traversal of the bucket split
     * tree. May also be the null bucket if the superbucket has not seen any "proper"
     * progress updates yet or the special case Integer.MAX_VALUE. Note that inconsistent
     * splitting might actually see <code>progress</code> as containing <code>super</code>
     * rather than vice versa, so this is explicitly allowed to pass by the code.
     */
    public void update(BucketId superbucket, BucketId progress) {
        // Delegate to bucket source, as it knows how to deal with buckets
        // that are in an inconsistent state wrt distribution bit count
        bucketSource.update(superbucket, progress, progressToken);
    }

   /**
    * @return The total number of iterable buckets that remain to be processed
    *
    * Note: currently includes all non-finished (i.e. active and pending
    * buckets) as well
    */
    public long getRemainingBucketCount() {
        return progressToken.getTotalBucketCount() - progressToken.getFinishedBucketCount();
    }

    /**
     * @return Internal bucket source instance. Do <i>NOT</i> modify!
     */
    protected BucketSource getBucketSource() {
        return bucketSource;
    }

    public ProgressToken getProgressToken() {
        return progressToken;
    }

    public int getDistributionBitCount() {
        return distributionBitCount;
    }

    /**
     * <p>Set the distribution bit count for the iterator and the buckets it
     * currently maintains and will return in the future.</p>
     *
     * <p>For document selections that result in a explicit set of buckets, this
     * is essentially a no-op, so in such a case, disregard the rest of this text.</p>
     *
     * <p>Changing the number of distribution bits for an unknown document
     * selection will effectively scale the bucket space that will be visited;
     * each bit increase or decrease doubling or halving its size, respectively.
     * When increasing, any pending buckets will be split to ensure the total
     * bucket space covered remains the same. Correspondingly, when decreasing,
     * any pending buckets will be merged appropriately.</p>
     *
     * <p>If there are buckets active at the time of the change, the actual
     * bucket splitting/merging operations are kept on hold until all active
     * buckets have been updated, at which point they will be automatically
     * performed. The iterator will force such an update by not giving out
     * any new or pending buckets until that happens.</p>
     *
     * <p><em>Note:</em> when decreasing the number of distribution bits,
     * there is a chance of losing superbucket progress in a bucket that
     * is merged with another bucket, leading to potential duplicate
     * results.</p>
     *
     * @param distBits New system state distribution bit count
     */
    public void setDistributionBitCount(int distBits) {
        if (distributionBitCount != distBits) {
            bucketSource.setDistributionBitCount(distBits, progressToken);
            distributionBitCount = distBits;
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Set visitor iterator distribution bit count to "
                        + distBits);
            }
        }
    }

    public boolean visitsAllBuckets() {
        return bucketSource.visitsAllBuckets();
    }

    /**
     * Create a new <code>VisitorIterator</code> instance based on the given document
     * selection string.
     *
     * @param documentSelection Document selection string used to create the
     * <code>VisitorIterator</code> instance. Depending on the characteristics of the
     * selection, the iterator may iterate over only a small subset of the buckets or
     * every bucket in the system. Both cases will be handled efficiently.
     * @param idFactory {@link BucketId} factory specifying the number of distribution bits
     * to use et al.
     * @param progress A unique {@link ProgressToken} instance which is used for maintaining the state
     * of the iterator. Can <em>not</em> be shared with other iterator instances at the same time.
     * If <code>progress</code> contains work done in an earlier iteration run, the iterator will pick
     * up from where it left off
     * @return A new <code>VisitorIterator</code> instance
     * @throws ParseException if <code>documentSelection</code> fails to properly parse
     */
    public static VisitorIterator createFromDocumentSelection(
            String documentSelection,
            BucketIdFactory idFactory,
            int distributionBitCount,
            ProgressToken progress) throws ParseException {
        BucketSelector bucketSel = new BucketSelector(idFactory);
        Set<BucketId> rawBuckets = bucketSel.getBucketList(documentSelection);
        BucketSource src;

        // Depending on whether the expression yielded an unknown number of
        // buckets, we create either an explicit bucket source or a distribution
        // bit-based range source
        if (rawBuckets == null) {
            // Range source
            src = new DistributionRangeBucketSource(distributionBitCount, progress);
        } else {
            // Explicit source
            src = new ExplicitBucketSource(rawBuckets, distributionBitCount, progress);
        }

        return new VisitorIterator(progress, src);
    }

    /**
     * Create a new <code>VisitorIterator</code> instance based on the given
     * set of buckets. This is supported for internal use only, and is required
     * by Synchronization. Use {@link #createFromDocumentSelection} instead for
     * all normal purposes.
     *
     * @param bucketsToVisit The set of buckets that will be visited
     * @param distributionBitCount Number of distribution bits to use
     * @param progress A unique ProgressToken instance which is used for maintaining the state
     * of the iterator. Can <em>not</em> be shared with other iterator instances at the same time.
     * If <code>progress</code> contains work done in an earlier iteration run, the iterator will pick
     * up from where it left off
     * @return A new <code>VisitorIterator</code> instance
     */
    public static VisitorIterator createFromExplicitBucketSet(
            Set<BucketId> bucketsToVisit,
            int distributionBitCount,
            ProgressToken progress) {
        // For obvious reasons, always create an explicit source here
        BucketSource src = new ExplicitBucketSource(bucketsToVisit,
                distributionBitCount, progress);
        return new VisitorIterator(progress, src);
    }
}
