// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.yahoo.document.BucketId;
import com.yahoo.io.GrowableByteBuffer;
import java.util.logging.Level;
import com.yahoo.vespa.objects.BufferSerializer;

/**
 * Token to use to keep track of progress for visiting. Can be used to resume
 * visiting if visiting has been aborted for any reason.
 *
 * @author Thomas Gundersen
 * @author vekterli
 */
public class ProgressToken {

    private static final Logger log = Logger.getLogger(ProgressToken.class.getName());

    /**
     * Any bucket kept track of by a <code>ProgressToken</code> instance may
     * be in one of two states: pending or active. <em>Pending</em> means that
     * a bucket may be returned by a VisitorIterator, i.e. it is ready for
     * visiting, while <em>active</em> means that a bucket is currently being
     * visited and may thus not be returned from an iterator.
     *
     * Getting a pending bucket via the iterator sets its state to active and
     * updating an active bucket sets its state back to pending again.
     */
    public enum BucketState {
        BUCKET_PENDING,
        BUCKET_ACTIVE
    }

    public static final BucketId NULL_BUCKET = new BucketId();
    public static final BucketId FINISHED_BUCKET = new BucketId(Integer.MAX_VALUE);

    /**
     * When a bucket has its state kept by the progress token, we need to
     * discern between active buckets (i.e. those that have been returned by
     * {@link com.yahoo.documentapi.VisitorIterator#getNext()} but have not
     * yet been update()'d) and pending buckets (i.e. those that have been
     * update()'d and may be returned by getNext() at some point)
     */
    public static class BucketEntry {
        private BucketId progress;
        private BucketState state;

        private BucketEntry(BucketId progress, BucketState state) {
            this.progress = progress;
            this.state = state;
        }

        public BucketId getProgress() {
            return progress;
        }

        public void setProgress(BucketId progress) {
            this.progress = progress;
        }

        public BucketState getState() {
            return state;
        }

        public void setState(BucketState state) {
            this.state = state;
        }
    }

    /**
     * For consistent bucket key ordering, we need to ensure that reverse bucket
     * IDs that have their MSB set actually are compared as being greater than
     * those that don't. This is yet another issue caused by Java's lack of
     * unsigned integers.
     */
    public static class BucketKeyWrapper implements Comparable<BucketKeyWrapper>
    {
        private long key;

        public BucketKeyWrapper(long key) {
            this.key = key;
        }

        public int compareTo(BucketKeyWrapper other) {
            if ((key & 0x8000000000000000L) != (other.key & 0x8000000000000000L)) {
                // MSBs differ
                return ((key >>> 63) > (other.key >>> 63)) ? 1 : -1;
            }
            // Mask off MSBs since we've already checked them, and with MSB != 1
            // we know the ordering will be consistent
            if ((key & 0x7FFFFFFFFFFFFFFFL) < (other.key & 0x7FFFFFFFFFFFFFFFL)) {
                return -1;
            } else if ((key & 0x7FFFFFFFFFFFFFFFL) > (other.key & 0x7FFFFFFFFFFFFFFFL)) {
                return 1;
            }
            return 0;
        }

        public long getKey() {
            return key;
        }

        public BucketId toBucketId() {
            return new BucketId(keyToBucketId(key));
        }

        @Override
        public String toString() {
            return Long.toHexString(key);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || !(o instanceof BucketKeyWrapper)) return false;
            return key == ((BucketKeyWrapper)o).key;
        }

        @Override
        public int hashCode() {
            return (int) (key ^ (key >>> 32));
        }
    }

    /**
     * By default, a ProgressToken's distribution bit count is set to the VDS
     * standard value of 16, but it may be changed via the iterator using it
     * or by a bucket source when importing an existing progress
     */
    private int distributionBits = 16;

    private TreeMap<BucketKeyWrapper, BucketEntry> buckets = new TreeMap<BucketKeyWrapper, BucketEntry>();
    private long activeBucketCount = 0;
    private long pendingBucketCount = 0;
    private long finishedBucketCount = 0;
    private long totalBucketCount = 0;
    private TreeMap<BucketId, BucketId> failedBuckets = new TreeMap<BucketId, BucketId>();
    private String firstErrorMsg;

    /**
     * The bucket cursor (i.e. current position in the bucket space) is used
     * by the range source
     */
    private long bucketCursor = 0;

    /**
     * Set by the VisitorIterator during a distribution bit change when
     * the token contains active/pending buckets with different used-bits
     */
    private boolean inconsistentState = false;

    /**
     * Creates a progress token.
     */
    public ProgressToken() {
    }

    public ProgressToken(int distributionBits) {
        this.distributionBits = distributionBits;
    }

    public ProgressToken(String serialized) {
        String[] lines = serialized.split("\\n");
        if (lines.length < 5) {
            throw new IllegalArgumentException("Progress file is malformed or a deprecated version");
        }

        // 1st token is simple header text. Just check that it starts with
        // a known value. To be 5.0 backwards compatible, we do not check
        // the rest of the line.
        final String header = lines[0];
        if (!header.startsWith("VDS bucket progress file")) {
            throw new IllegalArgumentException("File does not appear to be a " +
                    "valid VDS progress file; expected first line to start with " +
                    "'VDS bucket progress file'");
        }
        // 2nd token contains the distribution bit count the progress file was
        // saved with
        distributionBits = Integer.parseInt(lines[1]);
        bucketCursor = Long.parseLong(lines[2]);
        finishedBucketCount = Long.parseLong(lines[3]);
        totalBucketCount = Long.parseLong(lines[4]);

        if (totalBucketCount == finishedBucketCount) {
            return; // We're done here
        }

        // The rest of the tokens are super:sub bucket progress pairs
        for (int i = 5; i < lines.length; ++i) {
            String[] buckets = lines[i].split(":");
            if (buckets.length != 2) {
                throw new IllegalArgumentException("Bucket progress file contained malformed line");
            }
            // Due to Java's fantastically broken handling of unsigned integer
            // conversion, the following workaround (i.e. hack) is used for now
            // (it was also used in the past for presumably the same reason).
            BucketId superId = new BucketId("BucketId(0x" + buckets[0] + ")");
            BucketId subId;
            if ("0".equals(buckets[1])) {
                subId = new BucketId();
            } else {
                subId = new BucketId("BucketId(0x" + buckets[1] + ")");
            }
            addBucket(superId, subId, BucketState.BUCKET_PENDING);
        }
    }

    public ProgressToken(byte[] serialized) {
        BufferSerializer in = new BufferSerializer(GrowableByteBuffer.wrap(serialized));
        distributionBits = in.getInt(null);
        bucketCursor = in.getLong(null);
        finishedBucketCount = in.getLong(null);
        totalBucketCount = in.getLong(null);

        int progressCount = in.getInt(null);
        for (int i = 0; i < progressCount; ++i) {
            long key = in.getLong(null);
            long value = in.getLong(null);
            addBucket(new BucketId(key), new BucketId(value), BucketState.BUCKET_PENDING);
        }
    }

    public synchronized byte[] serialize() {
        BufferSerializer out = new BufferSerializer(new GrowableByteBuffer());
        out.putInt(null, distributionBits);
        out.putLong(null, bucketCursor);
        out.putLong(null, finishedBucketCount);
        out.putLong(null, totalBucketCount);

        out.putInt(null, buckets.size());

        // Append individual bucket progress
        for (Map.Entry<BucketKeyWrapper, ProgressToken.BucketEntry> entry : buckets.entrySet()) {
            out.putLong(null, keyToBucketId(entry.getKey().getKey()));
            out.putLong(null, entry.getValue().getProgress().getRawId());
        }

        byte[] ret = new byte[out.getBuf().position()];
        out.getBuf().rewind();
        out.getBuf().get(ret);
        return ret;
    }

    /** Returns a string (base64) encoding of the serial form of this token */
    public String serializeToString() {
        return Base64.getUrlEncoder().encodeToString(serialize());
    }

    public static ProgressToken fromSerializedString(String serializedString) {
        byte[] serialized;
        try {
            serialized = Base64.getUrlDecoder().decode(serializedString);
        } catch (IllegalArgumentException e) {
            // Legacy visitor tokens were encoded with MIME Base64 which may fail decoding as URL-safe.
            // Try again with MIME decoder to avoid breaking upgrade scenarios.
            // TODO(vekterli): remove once this is no longer a risk.
            serialized = Base64.getMimeDecoder().decode(serializedString);
        }
        return new ProgressToken(serialized);
    }

    public void addFailedBucket(BucketId superbucket, BucketId progress, String errorMsg) {
        BucketId existing = failedBuckets.put(superbucket, progress);
        if (existing != null) {
            throw new IllegalStateException(
                    "Attempting to add a superbucket to failed buckets that has already been added: "
                            + superbucket + ":" + progress);
        }
        if (firstErrorMsg == null) {
            firstErrorMsg = errorMsg;
        }
    }

    /**
     * Get all failed buckets and their progress. Not thread safe.
     * @return Unmodifiable map of all failed buckets
     */
    public Map<BucketId, BucketId> getFailedBuckets() {
        return Collections.unmodifiableMap(failedBuckets);
    }

    /**
     * Updates internal progress state for <code>bucket</code>, indicating it's currently
     * at <code>progress</code>. Assumes that given a completely finished bucket, this
     * function will not be called again to further update its progress after
     * the finished-update.
     *
     * @see VisitorIterator#update(com.yahoo.document.BucketId, com.yahoo.document.BucketId)
     *
     * @param superbucket A valid superbucket ID that exists in <code>buckets</code>
     * @param progress The sub-bucket progress that has been reached in the
     * superbucket
     */
    protected void updateProgress(BucketId superbucket, BucketId progress) {
        // There exists a valid case in which the progress bucket may actually contains
        // its superbucket from the POV of the storage code, so it has to be handled
        // appropriately.
        if (!progress.equals(NULL_BUCKET)
                && !progress.equals(FINISHED_BUCKET)
                && !superbucket.contains(progress)
                && !progress.contains(superbucket)) {
            if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "updateProgress called with non-contained bucket "
                       + "pair " + superbucket + ":" + progress + ", but allowing anyway");
                }
        }

        BucketKeyWrapper superKey = bucketToKeyWrapper(superbucket);
        BucketEntry entry = buckets.get(superKey);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "updateProgress with unknown superbucket "
                    + superbucket + ":" + progress);
        }

        // If progress == Integer.MAX_VALUE, we're done. Otherwise, we're not
        if (!progress.equals(FINISHED_BUCKET)) {
            if (entry.getState() != BucketState.BUCKET_ACTIVE) {
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "updateProgress called with sub-bucket that was "
                       + "not marked as active " + superbucket + ":" + progress);
                }
            } else {
                assert(activeBucketCount > 0);
                --activeBucketCount;
                ++pendingBucketCount;
            }
            // Mark bucket as pending instead of active, allowing it to be
            // reused by the iterator
            entry.setState(BucketState.BUCKET_PENDING);
            entry.setProgress(progress);
        }
        else {
            // Superbucket is finished, alongside its sub-bucket tree
            ++finishedBucketCount;
            if (entry.getState() == BucketState.BUCKET_PENDING) {
                assert(pendingBucketCount > 0);
                --pendingBucketCount;
            } else {
                assert(activeBucketCount > 0);
                --activeBucketCount;
            }
            buckets.remove(superKey);
        }
    }

    /**
     * <em>For use internally by DocumentAPI code only</em>. Using this method by
     * itself will invariably lead to undefined ProgressToken state unless
     * care is taken. Leave it to the VisitorIterator.
     *
     * @param superbucket Superbucket that will be progress-tracked
     * @param progress Bucket progress thus far
     * @param state Initial bucket state. Only pending buckets may be returned
     */
    protected void addBucket(BucketId superbucket, BucketId progress, BucketState state) {
        if (progress.equals(FINISHED_BUCKET)) {
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "Trying to add already finished superbucket "
                        + superbucket + "; ignoring it");
            }
            return;
        }
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST, "Adding bucket pair " + superbucket
                    + ":" + progress + " with state " + state);
        }

        BucketEntry entry = new BucketEntry(progress, state);
        BucketEntry existing = buckets.put(bucketToKeyWrapper(superbucket), entry);
        if (existing != null) {
            throw new IllegalStateException(
                    "Attempting to add a superbucket that has already been added: "
                    + superbucket + ":" + progress);
        }
        if (state == BucketState.BUCKET_PENDING) {
            ++pendingBucketCount;
        } else {
            ++activeBucketCount;
        }
    }

    /**
     * Marks the current bucket as finished and advances the bucket cursor;
     * throws instead if the current bucket is already {@link #addBucket added}.
     */
    void skipCurrentBucket() {
        if (buckets.containsKey(bucketToKeyWrapper(getCurrentBucketId())))
            throw new IllegalStateException("Current bucket was already added to the explicit bucket set");

        ++finishedBucketCount;
        ++bucketCursor;
    }

    /**
     * Directly generate a bucket Id key for the <code>n</code>th bucket in
     * reverse sorted order.
     *
     * @param n a number in the range [0, 2**<code>distributionBits</code>)
     * @param distributionBits Distribution bit count for the generated key
     * @return A value where, if you had generated 2**<code>distributionBits</code>
     * {@link BucketId}s with incremental numerical IDs and then sorted
     * them on their reverse bit-order keys, the returned key would be equal
     * to the nth element in the resulting sorted sequence.
     */
    public static long makeNthBucketKey(long n, int distributionBits) {
        return (n << (64 - distributionBits)) | distributionBits;
    }

    public int getDistributionBitCount() {
        return distributionBits;
    }

    /**
     * Set the internal number of distribution bits, which wil be used for writing
     * the progress file and calculating correct percent-wise sub-bucket completion.
     *
     * Note that simply invoking this method on the progress token does not actually
     * change any of its bucket structures/counts! <i>This is the bucket source's
     * responsibility</i>, since only it knows how such a change will affect the
     * progress semantics.
     *
     * @param distributionBits new distribution bit value
     */
    protected void setDistributionBitCount(int distributionBits) {
        this.distributionBits = distributionBits;
    }

    public long getActiveBucketCount() {
        return activeBucketCount;
    }

    public long getBucketCursor() {
        return bucketCursor;
    }

    static BucketId toBucketId(long bucketCursor, int distributionBits) {
        return new BucketId(keyToBucketId(makeNthBucketKey(bucketCursor, distributionBits)));
    }

    BucketId getCurrentBucketId() {
        return toBucketId(getBucketCursor(), getDistributionBitCount());
    }

    protected void setBucketCursor(long bucketCursor) {
        this.bucketCursor = bucketCursor;
    }

    public long getFinishedBucketCount() {
        return finishedBucketCount;
    }

    /**
     * <em>For use by bucket sources and unit tests only!</em>
     *
     * @param finishedBucketCount Number of buckets the token has finished
     */
    protected void setFinishedBucketCount(long finishedBucketCount) {
        this.finishedBucketCount = finishedBucketCount;
    }

    public long getTotalBucketCount() {
        return totalBucketCount;
    }

    /**
     * <em>For use by bucket sources and unit tests only!</em>
     *
     * @param totalBucketCount Total number of buckets that the progress token spans
     */
    protected void setTotalBucketCount(long totalBucketCount) {
        this.totalBucketCount = totalBucketCount;
    }

    public long getPendingBucketCount() {
        return pendingBucketCount;
    }

    public boolean hasPending() {
        return pendingBucketCount > 0;
    }

    public boolean hasActive() {
        return activeBucketCount > 0;
    }

    public boolean isFinished() {
        return finishedBucketCount == totalBucketCount;
    }

    public boolean isEmpty() {
        return buckets.isEmpty();
    }

    public String getFirstErrorMsg() {
        return firstErrorMsg;
    }

    public boolean containsFailedBuckets() {
        return !failedBuckets.isEmpty();
    }

    public boolean isInconsistentState() {
        return inconsistentState;
    }

    public void setInconsistentState(boolean inconsistentState) {
        this.inconsistentState = inconsistentState;
    }

    /**
     * Get internal progress token bucket state map. <em>For internal use only!</em>
     * @return Map of superbuckets → sub buckets
     */
    protected TreeMap<BucketKeyWrapper, BucketEntry> getBuckets() {
        return buckets;
    }

    protected void setActiveBucketCount(long activeBucketCount) {
        this.activeBucketCount = activeBucketCount;
    }

    protected void setPendingBucketCount(long pendingBucketCount) {
        this.pendingBucketCount = pendingBucketCount;
    }

    /**
     * The format of the bucket progress output is as follows:
     * <pre>
     *   VDS bucket progress file (n% completed)\n
     *   distribution bit count\n
     *   current bucket cursor\n
     *   number of finished buckets\n
     *   total number of buckets\n
     *   hex-of-superbucket:hex-of-progress\n
     *   ... repeat above line for each pending bucket ...
     * </pre>
     *
     * Note that unlike earlier versions of ProgressToken, the bucket IDs are
     * not prefixed with '0x'.
     */
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        // Append header
        sb.append("VDS bucket progress file (");
        sb.append(percentFinished());
        sb.append("% completed)\n");
        sb.append(distributionBits);
        sb.append('\n');
        sb.append(bucketCursor);
        sb.append('\n');
        long doneBucketCount = Math.max(0l, finishedBucketCount - failedBuckets.size());
        sb.append(doneBucketCount);
        sb.append('\n');
        sb.append(totalBucketCount);
        sb.append('\n');
        // Append individual bucket progress
        for (Map.Entry<BucketKeyWrapper, ProgressToken.BucketEntry> entry : buckets.entrySet()) {
            sb.append(Long.toHexString(keyToBucketId(entry.getKey().getKey())));
            sb.append(':');
            sb.append(Long.toHexString(entry.getValue().getProgress().getRawId()));
            sb.append('\n');
        }
        for (Map.Entry<BucketId, BucketId> entry : failedBuckets.entrySet()) {
            sb.append(Long.toHexString(entry.getKey().getRawId()));
            sb.append(':');
            sb.append(Long.toHexString(entry.getValue().getRawId()));
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Calculate an estimate on how far we've managed to iterate over both the
     * superbuckets and the sub-buckets.
     *
     * Runs in <em>O(n+m)</em> time, where <em>n</em> is the number of active buckets
     * and <em>m</em> is the number of pending buckets. Both these values should
     * be fairly small in practice, however.
     *
     * Method is synchronized, as legacy code treats this as an atomic read.
     *
     * @return A value in the range [0, 100] estimating the progress.
     */
    public synchronized double percentFinished() {
        long superTotal = totalBucketCount;
        long superFinished = finishedBucketCount;

        if (superTotal == 0 || superTotal == superFinished) return 100;

        double superDelta = 100.0 / superTotal;
        double cumulativeSubProgress = 0;

        // Calculate cumulative for all non-finished buckets. 0 means the
        // bucket has yet to see any progress
        // There are numerical precision issues here, but this hardly requires
        // aerospace engineering result-accuracy
        for (Map.Entry<BucketKeyWrapper, ProgressToken.BucketEntry> entry : buckets.entrySet()) {
            BucketId superbucket = new BucketId(keyToBucketId(entry.getKey().getKey()));
            BucketId progress = entry.getValue().getProgress();
            // Prevent calculation of bucket progress on inconsistent buckets
            if (progress.getId() != 0 && superbucket.contains(progress)) {
                cumulativeSubProgress += superDelta * progressFraction(superbucket, progress);
            }
        }

        return (((double)superFinished / (double)superTotal) * 100.0)
                + cumulativeSubProgress;
    }

    /*
     * Based on the following C++ code from document/bucket/bucketid.cpp:
     *
     * BucketId::Type
     * BucketId::bucketIdToKey(Type id)
     * {
     *   Type retVal = reverse(id);
     *
     *   Type usedCountLSB = id >> maxNumBits();
     *   retVal >>= CountBits;
     *   retVal <<= CountBits;
     *   retVal |= usedCountLSB;
     *
     *   return retVal;
     * }
     *
     * static uint32_t maxNumBits() { return (8 * sizeof(Type) - CountBits);}
     */
    // TODO: this should probably be moved to BucketId at some point?
    public static long bucketToKey(long id) {
        long retVal = Long.reverse(id);
        long usedCountLSB = id >>> (64 - BucketId.COUNT_BITS);
        retVal >>>= BucketId.COUNT_BITS;
        retVal <<= BucketId.COUNT_BITS;
        retVal |= usedCountLSB;

        return retVal;
    }

    private static BucketKeyWrapper bucketToKeyWrapper(BucketId bucket) {
        return new BucketKeyWrapper(bucketToKey(bucket.getId()));
    }
    /*
     * BucketId::Type
     * BucketId::keyToBucketId(Type key)
     * {
     *   Type retVal = reverse(key);
     *
     *   Type usedCountMSB = key << maxNumBits();
     *   retVal <<= CountBits;
     *   retVal >>= CountBits;
     *   retVal |= usedCountMSB;
     *
     *   return retVal;
     * }
     */
    public static long keyToBucketId(long key) {
        long retVal = Long.reverse(key);
        long usedCountMSB = key << (64 - BucketId.COUNT_BITS);
        retVal <<= BucketId.COUNT_BITS;
        retVal >>>= BucketId.COUNT_BITS;
        retVal |= usedCountMSB;

        return retVal;
    }

    /**
     * @param superbucket The superbucket of which <code>progress</code> is
     * a sub-bucket
     * @param progress The sub-bucket for which a fractional progress should
     * be calculated
     * @return a value in [0, 1] specifying how far the (sub-bucket) has
     * reached in its superbucket. This is calculated by looking at the
     * bucket's split factor.
     */
    public synchronized double progressFraction(BucketId superbucket, BucketId progress) {
        long revBits = bucketToKey(progress.getId());
        int superUsed = superbucket.getUsedBits();
        int progressUsed = progress.getUsedBits();

        if (progressUsed == 0 || progressUsed < superUsed) {
            return 0;
        }

        int splitCount = progressUsed - superUsed;

        if (splitCount == 0) return 1; // Superbucket or inconsistent used-bits

        // Extract reversed split-bits
        revBits <<= superUsed;
        revBits >>>= 64 - splitCount;

        return (double)(revBits + 1) / (double)(1L << splitCount);
    }

    /**
     * Checks whether or not a given bucket is certain to be finished. Only
     * looks at the super-bucket part of the given bucket ID, so it's possible
     * that the bucket has in fact finished on a sub-bucket progress level.
     * This does not affect the correctness of the result, however.
     *
     * During a distribution bit change, the token's buckets may be inconsistent.
     * In this scenario, false is always returned since we can't tell for
     * sure if the bucket is still active until the buckets have been made
     * consistent.
     *
     * @param bucket Bucket to check whether or not is finished.
     * @return <code>true</code> if <code>bucket</code>'s super-bucket is
     * finished, <code>false</code> otherwise.
     */
    protected synchronized boolean isBucketFinished(BucketId bucket) {
        if (inconsistentState) {
            return false;
        }
        // Token only knows of super-buckets, not sub buckets
        BucketId superbucket = new BucketId(distributionBits, bucket.getId());
        // Bucket is done if the current cursor location implies a visitor for
        // the associated superbucket has already been sent off at some point
        // and there is no pending visitor for the superbucket. The cursor is
        // used to directly generate bucket keys, so we can compare against it
        // directly.
        // Example: given db=3 and cursor=2, the buckets 000 and 100 will have
        // been returned by the iterator. By reversing the id and "right-
        // aligning" it, we get the cursor location that would be required to
        // generate it.
        // We also return false if we're inconsistent, since the active/pending
        // check is done on exact key values, requiring a uniform distribution
        // bit value.
        long reverseId = Long.reverse(superbucket.getId())
                >>> (64 - distributionBits); // No count bits

        if (reverseId >= bucketCursor) {
            return false;
        }
        // Bucket has been generated, and it must have been finished if it's
        // not listed as active/pending since we always remove finished buckets
        BucketEntry entry = buckets.get(bucketToKeyWrapper(superbucket));
        if (entry == null) {
            return true;
        }
        // If key of bucket progress > key of bucket id, we've finished it
        long bucketKey = bucketToKey(bucket.getId());
        long progressKey = bucketToKey(entry.getProgress().getId());
        // TODO: verify correctness for all bucket orderings!
        return progressKey > bucketKey;
    }

    /**
     *
     * @param bucket BucketId to be split into two buckets. Bucket's used-bits
     * do not need to match the ProgressToken's current distribution bit count,
     * as it is assumed the client knows what it's doing and will bring the
     * token into a consistent state eventually.
     */
    protected void splitPendingBucket(BucketId bucket) {
        BucketKeyWrapper bucketKey = bucketToKeyWrapper(bucket);
        BucketEntry entry = buckets.get(bucketKey);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Attempting to split unknown bucket: " + bucket);
        }
        if (entry.getState() != BucketState.BUCKET_PENDING) {
            throw new IllegalArgumentException(
                    "Attempting to split non-pending bucket: " + bucket);
        }

        int splitDistBits = bucket.getUsedBits() + 1;
        // Original bucket is replaced by two split children
        BucketId splitLeft = new BucketId(splitDistBits, bucket.getId());
        // Right split sibling becomes logically at location original_bucket*2 in the
        // bucket space due to the key ordering and setting the MSB of the split
        BucketId splitRight = new BucketId(splitDistBits, bucket.getId()
                | (1L << bucket.getUsedBits()));

        addBucket(splitLeft, entry.getProgress(), BucketState.BUCKET_PENDING);
        addBucket(splitRight, entry.getProgress(), BucketState.BUCKET_PENDING);

        // Remove old bucket
        buckets.remove(bucketKey);
        --pendingBucketCount;
    }

    protected void mergePendingBucket(BucketId bucket) {
        BucketKeyWrapper bucketKey = bucketToKeyWrapper(bucket);
        BucketEntry entry = buckets.get(bucketKey);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Attempting to join unknown bucket: " + bucket);
        }
        if (entry.getState() != BucketState.BUCKET_PENDING) {
            throw new IllegalArgumentException(
                    "Attempting to join non-pending bucket: " + bucket);
        }

        int usedBits = bucket.getUsedBits();
        // If MSB is 0, we should look for the bucket's right sibling. If not,
        // we know that there's no left sibling, as it should otherwise have been
        // merged already by the caller, due to it being ordered before the
        // right sibling in the pending mapping
        if ((bucket.getId() & (1L << (usedBits - 1))) == 0) {
            BucketId rightCheck = new BucketId(usedBits, bucket.getId() | (1L << (usedBits - 1)));
            BucketEntry rightSibling = buckets.get(bucketToKeyWrapper(rightCheck));
            // Must not merge if sibling isn't pending
            if (rightSibling != null) {
                assert(rightSibling.getState() == BucketState.BUCKET_PENDING);
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, "Merging " + bucket + " with rhs " + rightCheck);
                }
                // If right sibling has progress, it will unfortunately have to
                // be discarded
                if (rightSibling.getProgress().getUsedBits() != 0
                        && log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "Bucket progress for " + rightCheck +
                            " will be lost due to merging; potential for duplicates in result-set");
                }
                buckets.remove(bucketToKeyWrapper(rightCheck));
                --pendingBucketCount;
            }
        } else {
            BucketId leftSanityCheck = new BucketId(usedBits, bucket.getId() & ~(1L << (usedBits - 1)));
            BucketEntry leftSibling = buckets.get(bucketToKeyWrapper(leftSanityCheck));
            assert(leftSibling == null) : "bucket merge sanity checking failed";
        }

        BucketId newMerged = new BucketId(usedBits - 1, bucket.getId());
        addBucket(newMerged, entry.getProgress(), BucketState.BUCKET_PENDING);
        // Remove original bucket, leaving only the merged bucket
        buckets.remove(bucketKey);
        --pendingBucketCount;
        assert(pendingBucketCount > 0);
    }

    protected void setAllBucketsToState(BucketState state) {
        for (Map.Entry<BucketKeyWrapper, ProgressToken.BucketEntry> entry
                : buckets.entrySet()) {
            entry.getValue().setState(state);
        }
    }

    protected void clearAllBuckets() {
        buckets.clear();
        pendingBucketCount = 0;
        activeBucketCount = 0;
    }

}
