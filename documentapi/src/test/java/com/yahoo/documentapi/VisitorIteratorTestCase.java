// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for VisitorIterator and ProgressToken (kept in one test case because their
 * interactions are so tightly coupled)
 *
 * @author vekterli
 */
public class VisitorIteratorTestCase {

    @Test
    public void testIterationSingleBucketUpdate() throws ParseException {
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken progress = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.user = 1234", idFactory, 1, progress);

        assertFalse(progress.hasActive());
        assertEquals(progress.getPendingBucketCount(), 1);
        assertEquals(progress.getFinishedBucketCount(), 0);
        assertEquals(progress.getTotalBucketCount(), 1);
        assertFalse(iter.isDone());
        assertTrue(iter.hasNext());
        assertEquals(iter.getRemainingBucketCount(), 1);
        VisitorIterator.BucketProgress b1 = iter.getNext();
        // Upon first getNext of a superbucket, progress == 0
        assertEquals(b1.getSuperbucket(), new BucketId(32, 1234));
        assertEquals(b1.getProgress(), new BucketId());
        assertFalse(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(iter.getRemainingBucketCount(), 1);
        // Should only be one active bucket; the one we just got
        assertEquals(progress.getActiveBucketCount(), 1);
        // No pending yet
        assertFalse(progress.hasPending());
        // Update the bucket with a sub-bucket, moving it from active to pending
        BucketId sub = new BucketId(b1.getSuperbucket().getUsedBits() + 1, b1.getSuperbucket().getId());
        iter.update(b1.getSuperbucket(), sub);
        assertFalse(progress.hasActive());
        assertEquals(progress.getPendingBucketCount(), 1);
        assertTrue(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(iter.getRemainingBucketCount(), 1);
        // Get the pending bucket
        VisitorIterator.BucketProgress b2 = iter.getNext();
        assertEquals(b2.getSuperbucket(), new BucketId(32, 1234));
        assertEquals(b2.getProgress(), new BucketId(33, 1234));
        assertFalse(iter.hasNext());
        assertEquals(progress.getActiveBucketCount(), 1);
        assertFalse(progress.hasPending());
        // Now update with progress==super, signalling that the bucket is done
        iter.update(b1.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        assertFalse(progress.hasActive());
        assertFalse(progress.hasPending());
        assertFalse(iter.hasNext());
        assertTrue(iter.isDone());
        assertTrue(progress.isFinished());
        assertEquals(progress.getFinishedBucketCount(), 1);
        assertEquals(iter.getRemainingBucketCount(), 0);
    }

    @Test
    public void testProgressSerializationRange() throws ParseException {
        int distBits = 4;

        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken progress = new ProgressToken();

        // docsel will be unknown --> entire bucket range will be covered
        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, distBits, progress);

        assertEquals(progress.getDistributionBitCount(), distBits);
        assertTrue(iter.getBucketSource() instanceof VisitorIterator.DistributionRangeBucketSource);

        assertEquals(progress.getFinishedBucketCount(), 0);
        assertEquals(progress.getTotalBucketCount(), 1 << distBits);

        // First, get+update half of the buckets, marking them as done
        long bucketCount = 0;
        long bucketStop = 1 << (distBits - 1);

        while (iter.hasNext() && bucketCount != bucketStop) {
            VisitorIterator.BucketProgress ids = iter.getNext();
            iter.update(ids.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
            ++bucketCount;
        }
        assertEquals(bucketCount, bucketStop);
        // Should be no buckets in limbo at this point
        assertFalse(progress.hasActive());
        assertFalse(progress.hasPending());
        assertFalse(iter.isDone());
        assertTrue(iter.hasNext());
        assertEquals(progress.getFinishedBucketCount(), bucketCount);
        assertFalse(progress.isFinished());

        StringBuilder desired = new StringBuilder();
        desired.append("VDS bucket progress file (50.0% completed)\n");
        desired.append(distBits);
        desired.append('\n');
        desired.append(bucketCount); // Finished == cursor for this
        desired.append('\n');
        desired.append(bucketCount);
        desired.append('\n');
        desired.append(1 << distBits);
        desired.append('\n');

        assertEquals(desired.toString(), progress.toString());

        // Test import, in which case distribution bits are 1
        BucketIdFactory idFactory2 = new BucketIdFactory();

        // De-serialization with no pending buckets
        {
            ProgressToken progDs = new ProgressToken(progress.toString());

            assertEquals(progDs.getDistributionBitCount(), distBits);
            assertEquals(progDs.getTotalBucketCount(), 1 << distBits);
            assertEquals(progDs.getFinishedBucketCount(), bucketCount);

            VisitorIterator iterDs = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory2, 1, progDs);

            assertFalse(progDs.hasPending());
            assertFalse(progDs.hasActive());
            assertTrue(iterDs.hasNext());
            assertFalse(iterDs.isDone());
            assertEquals(distBits, iterDs.getDistributionBitCount());
            assertEquals(distBits, progDs.getDistributionBitCount());

            // Iterator must start up on next bucket in range
            VisitorIterator.BucketProgress idDs = iterDs.getNext();
            long resumeKey = ProgressToken.makeNthBucketKey(bucketCount, distBits);
            assertEquals(idDs.getSuperbucket(), new BucketId(ProgressToken.keyToBucketId(resumeKey)));
            assertEquals(idDs.getProgress(), new BucketId());
        }

        // Now fetch a subset of the remaining buckets without finishing them,
        // keeping some in the active set and some in pending
        int pendingTotal = 1 << (distBits - 3);
        int activeTotal = 1 << (distBits - 3);
        Vector<VisitorIterator.BucketProgress> buckets = new Vector<VisitorIterator.BucketProgress>();

        // Pre-fetch, since otherwise we'd reuse pending buckets
        for (int i = 0; i < pendingTotal + activeTotal; ++i) {
            buckets.add(iter.getNext());
        }

        for (int i = 0; i < pendingTotal + activeTotal; ++i) {
            VisitorIterator.BucketProgress idTemp = buckets.get(i);
            if (i < activeTotal) {
                // Make them 50% done
                iter.update(idTemp.getSuperbucket(),
                        new BucketId(distBits + 2, idTemp.getSuperbucket().getId() | (2 << distBits)));
            }
            // else: leave hanging as active
        }

        assertEquals(progress.getActiveBucketCount(), activeTotal);
        assertEquals(progress.getPendingBucketCount(), pendingTotal);

        // we can't reuse the existing string builder, since the bucket cursor
        // has changed
        desired = new StringBuilder();
        desired.append("VDS bucket progress file (").append(progress.percentFinished()).append("% completed)\n");
        desired.append(distBits);
        desired.append('\n');
        desired.append(bucketCount + pendingTotal + activeTotal);
        desired.append('\n');
        desired.append(bucketCount);
        desired.append('\n');
        desired.append(1 << distBits);
        desired.append('\n');

        assertEquals(progress.getBuckets().entrySet().size(), pendingTotal + activeTotal);

        for (Map.Entry<ProgressToken.BucketKeyWrapper, ProgressToken.BucketEntry> entry
                : progress.getBuckets().entrySet()) {
            desired.append(Long.toHexString(ProgressToken.keyToBucketId(entry.getKey().getKey())));
            desired.append(':');
            desired.append(Long.toHexString(entry.getValue().getProgress().getRawId()));
            desired.append('\n');
        }

        assertEquals(progress.toString(), desired.toString());

        {
            // Deserialization with pending buckets
            ProgressToken progDs = new ProgressToken(progress.toString());

            assertEquals(progDs.getDistributionBitCount(), distBits);
            assertEquals(progDs.getTotalBucketCount(), 1 << distBits);
            assertEquals(progDs.getFinishedBucketCount(), bucketCount);

            VisitorIterator iterDs = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory2, 1, progDs);

            // All started but nonfinished buckets get placed in pending upon
            // deserialization
            assertEquals(progDs.getPendingBucketCount(), pendingTotal + activeTotal);
            assertEquals(distBits, progDs.getDistributionBitCount());
            assertEquals(distBits, iterDs.getDistributionBitCount());
            assertFalse(progDs.hasActive());
            assertTrue(iterDs.hasNext());
            assertFalse(iterDs.isDone());
            assertEquals(progDs.getBucketCursor(), bucketCount + pendingTotal + activeTotal);
        }

        // Finish all the active buckets
        for (int i = activeTotal; i < activeTotal + pendingTotal; ++i) {
            iter.update(buckets.get(i).getSuperbucket(), ProgressToken.FINISHED_BUCKET);
            ++bucketCount;
        }

        assertEquals(progress.getActiveBucketCount(), 0);
        boolean consistentNext = true;
        // Get all pending/remaining sourced and finish them all
        while (!iter.isDone()) {
            if (!iter.hasNext()) {
                consistentNext = false;
                break;
            }
            VisitorIterator.BucketProgress bp = iter.getNext();
            iter.update(bp.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
            ++bucketCount;
        }

        assertTrue(consistentNext);
        assertFalse(iter.hasNext());
        assertTrue(progress.isFinished());
        // Cumulative number of finished buckets must match 2^distbits
        assertEquals(bucketCount, 1 << distBits);
        StringBuilder finished = new StringBuilder();
        finished.append("VDS bucket progress file (100.0% completed)\n");
        finished.append(distBits);
        finished.append('\n');
        finished.append(1 << distBits); // Cursor
        finished.append('\n');
        finished.append(1 << distBits); // Finished
        finished.append('\n');
        finished.append(1 << distBits); // Total
        finished.append('\n');

        assertEquals(progress.toString(), finished.toString());
    }

    @Test
    public void testProgressSerializationExplicit() throws ParseException {
        int distBits = 16;

        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken progress = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.user == 1234 or id.user == 6789 or id.user == 8009", idFactory, distBits, progress);

        assertEquals(progress.getDistributionBitCount(), distBits);
        assertTrue(iter.getBucketSource() instanceof VisitorIterator.ExplicitBucketSource);

        assertEquals(progress.getFinishedBucketCount(), 0);
        assertEquals(progress.getTotalBucketCount(), 3);
        assertEquals(progress.getPendingBucketCount(), 3);

        VisitorIterator.BucketProgress bp1 = iter.getNext();
        VisitorIterator.BucketProgress bp2 = iter.getNext();
        assertEquals(progress.getPendingBucketCount(), 1);
        assertEquals(progress.getActiveBucketCount(), 2);
        // Buckets are ordered by their reverse bucket id key
        assertEquals(bp1.getSuperbucket(), new BucketId(32, 1234));
        assertEquals(bp1.getProgress(), new BucketId());
        // Put bucket 1234 back into pending
        iter.update(bp1.getSuperbucket(), new BucketId(36, 1234));
        assertEquals(progress.getPendingBucketCount(), 2);

        assertEquals(bp2.getSuperbucket(), new BucketId(32, 8009));
        assertEquals(bp2.getProgress(), new BucketId());

        {
            StringBuilder desired = new StringBuilder();
            desired.append("VDS bucket progress file (").append(progress.percentFinished()).append("% completed)\n");
            desired.append(distBits);
            desired.append('\n');
            desired.append(0);
            desired.append('\n');
            desired.append(0);
            desired.append('\n');
            desired.append(3);
            desired.append('\n');
            // Pending/active buckets are written in an increasing (key, not
            // bucket-id!) order
            desired.append(Long.toHexString(new BucketId(32, 1234).getRawId()));
            desired.append(':');
            desired.append(Long.toHexString(new BucketId(36, 1234).getRawId()));
            desired.append('\n');
            desired.append(Long.toHexString(new BucketId(32, 8009).getRawId()));
            desired.append(":0\n");
            desired.append(Long.toHexString(new BucketId(32, 6789).getRawId()));
            desired.append(":0\n");

            assertEquals(desired.toString(), progress.toString());

            ProgressToken prog2 = new ProgressToken(progress.toString());
            assertEquals(prog2.getDistributionBitCount(), distBits);
            assertEquals(prog2.getTotalBucketCount(), 3);
            assertEquals(prog2.getFinishedBucketCount(), 0);

            VisitorIterator iter2 = VisitorIterator.createFromDocumentSelection(
                "id.user == 1234 or id.user == 6789 or id.user == 8009", idFactory, distBits, prog2);

            assertEquals(prog2.getPendingBucketCount(), 3);
            assertFalse(prog2.hasActive());
            assertTrue(iter2.hasNext());
            assertFalse(iter2.isDone());

            assertTrue(iter2.getBucketSource() instanceof VisitorIterator.ExplicitBucketSource);
            assertFalse(iter2.getBucketSource().hasNext());

            VisitorIterator.BucketProgress bp = iter2.getNext();
            assertEquals(bp.getSuperbucket(), new BucketId(32, 1234));
            assertEquals(bp.getProgress(), new BucketId(36, 1234));
            assertEquals(prog2.getPendingBucketCount(), 2);

            assertTrue(iter2.hasNext());
            assertFalse(iter2.isDone());
            bp = iter2.getNext();
            assertEquals(bp.getSuperbucket(), new BucketId(32, 8009));
            assertEquals(bp.getProgress(), new BucketId());
            assertEquals(prog2.getPendingBucketCount(), 1);

            assertTrue(iter2.hasNext());
            assertFalse(iter2.isDone());
            bp = iter2.getNext();
            assertEquals(prog2.getPendingBucketCount(), 0);
            assertEquals(bp.getSuperbucket(), new BucketId(32, 6789));
            assertEquals(bp.getProgress(), new BucketId());
            assertFalse(iter2.hasNext());
            assertFalse(iter2.isDone()); // Active buckets
            assertEquals(prog2.getActiveBucketCount(), 3);
        }

        // Finish off all active buckets
        assertTrue(iter.hasNext());
        assertFalse(iter.isDone());
        bp1 = iter.getNext();
        assertEquals(bp1.getSuperbucket(), new BucketId(32, 1234));
        assertEquals(bp1.getProgress(), new BucketId(36, 1234));

        iter.update(bp1.getSuperbucket(), ProgressToken.FINISHED_BUCKET);

        assertTrue(iter.hasNext());
        assertFalse(iter.isDone());
        bp1 = iter.getNext();
        assertEquals(bp1.getSuperbucket(), new BucketId(32, 6789));
        assertEquals(bp1.getProgress(), new BucketId());

        // Just to make sure Java serializes the long properly
        assertEquals(
                progress.toString(),
                "VDS bucket progress file (" + progress.percentFinished() + "% completed)\n" +
                "16\n" +
                "0\n" +
                "1\n" +
                "3\n" +
                "8000000000001f49:0\n" +
                "8000000000001a85:0\n");

        iter.update(bp1.getSuperbucket(), ProgressToken.FINISHED_BUCKET);

        // At this point, we've got one active but no pending, so hasNext == false,
        // but isDone is also == false
        assertFalse(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(progress.getPendingBucketCount(), 0);
        assertEquals(progress.getActiveBucketCount(), 1);

        iter.update(bp2.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        assertFalse(iter.hasNext());
        assertTrue(iter.isDone());
        assertTrue(progress.isFinished());
        assertEquals(progress.getActiveBucketCount(), 0);

        {
            StringBuilder finished = new StringBuilder();
            finished.append("VDS bucket progress file (100.0% completed)\n");
            finished.append(distBits);
            finished.append('\n');
            finished.append(0); // Cursor (not used by explicit)
            finished.append('\n');
            finished.append(3); // Finished
            finished.append('\n');
            finished.append(3); // Total
            finished.append('\n');

            assertEquals(finished.toString(), progress.toString());
        }
    }

    /**
     * Test that doing update() on a bucket several times in a row (without re-fetching
     * from getNext first) works
     */
    @Test
    public void testActiveUpdate() throws ParseException {
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken progress = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group = \"yahoo.com\"", idFactory, 16, progress);

        VisitorIterator.BucketProgress bp = iter.getNext();

        assertEquals(progress.getPendingBucketCount(), 0);
        assertEquals(progress.getActiveBucketCount(), 1);

        BucketId superbucket = bp.getSuperbucket();
        int usedBits = superbucket.getUsedBits();

        iter.update(superbucket, new BucketId(usedBits + 2, superbucket.getId() | (2L << usedBits)));
        assertEquals(progress.getPendingBucketCount(), 1);
        assertEquals(progress.getActiveBucketCount(), 0);
        iter.update(superbucket, new BucketId(usedBits + 2, superbucket.getId() | (1L << usedBits)));
        assertEquals(progress.getPendingBucketCount(), 1);
        assertEquals(progress.getActiveBucketCount(), 0);

        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), superbucket);
        assertEquals(bp.getProgress(), new BucketId(usedBits + 2, superbucket.getId() | (1L << usedBits)));
        assertEquals(progress.getPendingBucketCount(), 0);
        assertEquals(progress.getActiveBucketCount(), 1);
    }

    /**
     * Test that ensures doing update(superbucket, 0) simply puts the bucket back in
     * pending
     */
    @Test
    public void testNullAndSuperUpdate() throws ParseException {
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken progress = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group = \"yahoo.com\"", idFactory, 16, progress);

        assertEquals(progress.getPendingBucketCount(), 1);

        VisitorIterator.BucketProgress bp = iter.getNext();
        assertEquals(bp.getProgress(), new BucketId());
        BucketId superbucket = bp.getSuperbucket();
        BucketId sub = bp.getProgress();

        assertFalse(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(progress.getPendingBucketCount(), 0);
        assertEquals(progress.getActiveBucketCount(), 1);

        // 0-bucket
        iter.update(superbucket, ProgressToken.NULL_BUCKET);
        assertTrue(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(progress.getPendingBucketCount(), 1);
        assertEquals(progress.getActiveBucketCount(), 0);

        VisitorIterator.BucketProgress bp2 = iter.getNext();
        assertEquals(bp2.getSuperbucket(), superbucket);
        assertEquals(bp2.getProgress(), ProgressToken.NULL_BUCKET);
        assertEquals(progress.getPendingBucketCount(), 0);
        assertEquals(progress.getActiveBucketCount(), 1);

        // progress == super
        iter.update(superbucket, superbucket);
        assertTrue(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(progress.getPendingBucketCount(), 1);
        assertEquals(progress.getActiveBucketCount(), 0);

        bp2 = iter.getNext();
        assertEquals(bp2.getSuperbucket(), superbucket);
        assertEquals(bp2.getProgress(), superbucket);
        assertEquals(progress.getPendingBucketCount(), 0);
        assertEquals(progress.getActiveBucketCount(), 1);
    }

    @Test
    public void testDeserializedFinishedProgress() {
        StringBuilder finished = new StringBuilder();
        finished.append("VDS bucket progress file\n"); // legacy; no completion percentage
        finished.append(17);
        finished.append('\n');
        finished.append(1L << 17); // Cursor
        finished.append('\n');
        finished.append(1L << 17); // Finished
        finished.append('\n');
        finished.append(1L << 17); // Total
        finished.append('\n');

        ProgressToken token = new ProgressToken(finished.toString());
        assertEquals(token.getDistributionBitCount(), 17);
        assertEquals(token.getTotalBucketCount(), 1L << 17);
        assertEquals(token.getFinishedBucketCount(), 1L << 17);
        assertEquals(token.getBucketCursor(), 1L << 17);
        assertTrue(token.isFinished());

        ProgressToken token2 = new ProgressToken(token.serialize());
        assertEquals(17, token2.getDistributionBitCount());
        assertEquals(1L << 17, token2.getTotalBucketCount());
        assertEquals(1L << 17, token2.getFinishedBucketCount());
        assertEquals(1L << 17, token2.getBucketCursor());
        assertTrue(token2.isFinished());
    }

    @Test
    public void testBucketProgressFraction() {
        double epsilon = 0.00001;
        // No progress
        BucketId b_0 = new BucketId();
        // No split; only superbucket (100%)
        BucketId b_100_0 = new BucketId(16, 1234);
        // 1 split (1/2)
        BucketId b_50_1 = new BucketId(17,  1234);
        BucketId b_100_1 = new BucketId(17, 1234 | (1 << 16));
        // 2 splits (1/4)
        BucketId b_25_2 = new BucketId(18, 1234);
        BucketId b_50_2 = new BucketId(18, 1234 | (2 << 16));
        BucketId b_75_2 = new BucketId(18, 1234 | (1 << 16));
        BucketId b_100_2 = new BucketId(18, 1234 | (3 << 16));

        ProgressToken p = new ProgressToken(16);

        BucketId sb = new BucketId(16, 1234);

        assertEquals(p.progressFraction(new BucketId(32, 1234), b_0), 0.0, epsilon);

        assertEquals(p.progressFraction(sb, b_100_0), 1.0, epsilon);

        assertEquals(p.progressFraction(sb, b_50_1),  0.5, epsilon);
        assertEquals(p.progressFraction(sb, b_100_1), 1.0, epsilon);

        assertEquals(p.progressFraction(sb, b_25_2),  0.25, epsilon);
        assertEquals(p.progressFraction(sb, b_50_2),  0.5,  epsilon);
        assertEquals(p.progressFraction(sb, b_75_2),  0.75, epsilon);
        assertEquals(p.progressFraction(sb, b_100_2), 1.0,  epsilon);

        assertEquals(p.progressFraction(new BucketId(0x8000000000000000L),
                new BucketId(0xb0000fff00000000L)), 1.0, epsilon);
    }

    @Test
    public void testProgressEstimation() throws ParseException {
        int distBits = 4;

        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken progress = new ProgressToken();

        // Create a range of [0, 16) superbuckets
        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, distBits, progress);

        assertEquals(progress.getDistributionBitCount(), 4);

        double epsilon = 0.00001;
        assertEquals(progress.percentFinished(), 0, epsilon);
        VisitorIterator.BucketProgress bp = iter.getNext();
        // Finish first superbucket (6.25% total)
        iter.update(bp.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        assertEquals(progress.percentFinished(), 6.25, epsilon);
        assertEquals(progress.getFinishedBucketCount(), 1);

        bp = iter.getNext();
        VisitorIterator.BucketProgress bp3 = iter.getNext();
        VisitorIterator.BucketProgress bp4 = iter.getNext();

        // Finish second (12.5% total)
        iter.update(bp.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        assertEquals(progress.percentFinished(), 12.5, epsilon);
        assertEquals(progress.getFinishedBucketCount(), 2);

        // Finish third bucket 75% through (17.1875% total)
        iter.update(bp3.getSuperbucket(), new BucketId(distBits + 2, bp3.getSuperbucket().getId() | (1 << distBits)));
        assertEquals(progress.percentFinished(), 17.1875, epsilon);
        assertEquals(progress.getFinishedBucketCount(), 2);

        // Finish fourth bucket 25% through (18.75% total)
        iter.update(bp4.getSuperbucket(), new BucketId(distBits + 2, bp4.getSuperbucket().getId()));
        assertEquals(progress.percentFinished(), 18.75, epsilon);
        assertEquals(progress.getFinishedBucketCount(), 2);
        // Finish all buckets
        iter.update(bp4.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        iter.update(bp3.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        assertEquals(progress.percentFinished(), 25, epsilon);
        assertEquals(progress.getFinishedBucketCount(), 4);

        while (iter.hasNext()) {
            bp = iter.getNext();
            iter.update(bp.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        }

        assertEquals(progress.getFinishedBucketCount(), 16);
        assertEquals(progress.percentFinished(), 100, epsilon);
    }

    @Test
    public void testBucketKeyWrapperOrdering() {
        ProgressToken.BucketKeyWrapper bk1 = new ProgressToken.BucketKeyWrapper(0x0000000000000001L);
        ProgressToken.BucketKeyWrapper bk2 = new ProgressToken.BucketKeyWrapper(0x7FFFFFFFFFFFFFFFL);
        ProgressToken.BucketKeyWrapper bk3 = new ProgressToken.BucketKeyWrapper(0x8000000000000000L);
        ProgressToken.BucketKeyWrapper bk4 = new ProgressToken.BucketKeyWrapper(0xFFFFFFFFFFFFFFFFL);
        assertTrue(bk1.compareTo(bk2) < 0);
        assertTrue(bk2.compareTo(bk3) < 0);
        assertTrue(bk3.compareTo(bk4) < 0);
        assertTrue(bk2.compareTo(bk1) > 0);
        assertTrue(bk3.compareTo(bk2) > 0);
        assertTrue(bk4.compareTo(bk3) > 0);
        ProgressToken.BucketKeyWrapper bk5 = new ProgressToken.BucketKeyWrapper(0x7FFFFFFFFFFFFFFFL);
        ProgressToken.BucketKeyWrapper bk6 = new ProgressToken.BucketKeyWrapper(0x8000000000000000L);
        assertTrue(bk5.compareTo(bk2) == 0);
        assertTrue(bk6.compareTo(bk3) == 0);
    }

    private void doTestBucketKeyGeneration(int db) {
        // Can't use longs since they won't sort properly when MSB is set
        ProgressToken.BucketKeyWrapper[] keys = new ProgressToken.BucketKeyWrapper[1 << db];

        // Generate entire bucket space for db
        for (int i = 0; i < (1 << db); ++i) {
            keys[i] = new ProgressToken.BucketKeyWrapper(
                    ProgressToken.bucketToKey(new BucketId(db, i).getId()));
        }
        Arrays.sort(keys);

        boolean consistentKeys = true;
        // Verify that makeNthBucketKey yields the same result as the equivalent
        // ordered value in the array of keys
        for (int i = 0; i < (1 << db); ++i) {
            long genKey = ProgressToken.makeNthBucketKey(i, db);
            long knownKey = keys[i].getKey();
            if (genKey != knownKey) {
                consistentKeys = false;
                break;
            }
        }
        assertTrue(consistentKeys);
    }

    @Test
    public void testBucketKeyGeneration() {
        // Due to the number of objects needed to be allocated, only test for a
        // small set of distribution bits
        for (int i = 1; i < 14; ++i) {
            doTestBucketKeyGeneration(i);
        }
    }

    @Test
    public void testSingleBucketSplits() throws ParseException {
        int db = 2;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        // Create a range of [0, 4) superbuckets
        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);
        VisitorIterator.BucketProgress bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db, 0));
        // Put back as pending
        iter.update(bp.getSuperbucket(), new BucketId());
        assertEquals(p.getPendingBucketCount(), 1);
        p.splitPendingBucket(new BucketId(db, 0));
        assertEquals(p.getPendingBucketCount(), 2);
        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db + 1, 0)); // left split
        assertEquals(bp.getProgress(), new BucketId(0));
        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db + 1, 4)); // right split
        assertEquals(bp.getProgress(), new BucketId(0));

        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db, 2));
        // Put back as pending, with a progress of 10010. This implies splitting
        // the bucket should set both splits with a progress to 10010
        iter.update(bp.getSuperbucket(), new BucketId(db + 3, 0x12));
        assertEquals(p.getPendingBucketCount(), 1);
        p.splitPendingBucket(new BucketId(db, 2));
        assertEquals(p.getPendingBucketCount(), 2);
        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db + 1, 2)); // left split
        assertEquals(bp.getProgress(), new BucketId(db + 3, 0x12));
        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db + 1, 6)); // right split
        assertEquals(bp.getProgress(), new BucketId(db + 3, 0x12));

        bp = iter.getNext();
        // Put back as pending with a progress of 10101. This implies splitting the
        // bucket should _discard_ left and set right's progress to 10101.
        // Update: no it shouldn't, we now split with equal progress without
        // discarding
        assertEquals(bp.getSuperbucket(), new BucketId(db, 1));
        iter.update(bp.getSuperbucket(), new BucketId(db + 3, 0x15));
        assertEquals(p.getPendingBucketCount(), 1);
        p.splitPendingBucket(new BucketId(db, 1));
        assertEquals(p.getPendingBucketCount(), 2);
        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db + 1, 1));
        assertEquals(bp.getProgress(), new BucketId(db + 3, 0x15));
        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db + 1, 5)); // right split
        assertEquals(bp.getProgress(), new BucketId(db + 3, 0x15));
    }

    /**
     * Test increasing the distribution bits for a full bucket space range
     * source with no finished, active or pending buckets
     * @throws ParseException upon docsel parse failure (shouldn't happen)
     */
    @Test
    public void testRangeDistributionBitIncrease1NoPending() throws ParseException {
        int db = 2;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        // Test for empty progress token. no splitting involved
        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        assertEquals(p.getTotalBucketCount(), 4);
        iter.setDistributionBitCount(db + 1);
        assertEquals(p.getTotalBucketCount(), 8);
        assertEquals(p.getDistributionBitCount(), db + 1);
        assertEquals(iter.getDistributionBitCount(), db + 1);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), db + 1);

        int[] desired = new int[] { 0, 4, 2, 6, 1, 5, 3, 7 };
        for (int i = 0; i < 8; ++i) {
            VisitorIterator.BucketProgress bp = iter.getNext();
            assertEquals(bp.getSuperbucket(), new BucketId(db + 1, desired[i]));
        }
    }

    @Test
    public void testRangeDistributionBitIncrease1AllBucketStates() throws ParseException {
        int db = 3;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        // For this test, have 1 finished bucket, 3 pending and 0 active (we
        // want to have the splitting to be triggered immediately)
        VisitorIterator.BucketProgress bp = iter.getNext();
        iter.update(bp.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        VisitorIterator.BucketProgress[] bpp = new VisitorIterator.BucketProgress[3];
        bpp[0] = iter.getNext();
        bpp[1] = iter.getNext();
        bpp[2] = iter.getNext();
        iter.update(bpp[0].getSuperbucket(), new BucketId());
        iter.update(bpp[1].getSuperbucket(), new BucketId());
        iter.update(bpp[2].getSuperbucket(), new BucketId());

        assertEquals(p.getFinishedBucketCount(), 1);
        assertEquals(p.getPendingBucketCount(), 3);
        assertEquals(p.getActiveBucketCount(), 0);

        iter.setDistributionBitCount(db + 1);

        assertEquals(p.getTotalBucketCount(), 16);
        assertEquals(p.getFinishedBucketCount(), 2);
        assertEquals(p.getPendingBucketCount(), 6);
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(p.getDistributionBitCount(), db + 1);
        assertEquals(iter.getDistributionBitCount(), db + 1);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), db + 1);

        // Bucket 3:0x4 -> 4:0x4 & 4:0xC
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x04));
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x0C));
        // Bucket 3:0x2 -> 4:0x2 & 4:0xA
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x02));
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x0A));
        // Bucket 3:0x6 -> 4:0x6 & 4:0xE
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x06));
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x0E));

        assertEquals(p.getPendingBucketCount(), 0);
        // Bucket source should now begin returning from bucket 4:0x1
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x01));
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x09));
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x05));
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x0D));
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(db + 1, 0x03));
        // Assume correct from here on
    }

    @Test
    public void testRangeDistributionIncreaseMultipleBits() throws ParseException {
        int db = 16;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        // For this test, have 3 finished bucket, 2 pending and 1 active
        for (int i = 0; i < 3; ++i) {
            iter.update(iter.getNext().getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        }

        VisitorIterator.BucketProgress[] bpp = new VisitorIterator.BucketProgress[2];
        bpp[0] = iter.getNext();
        bpp[1] = iter.getNext();
        VisitorIterator.BucketProgress bpa = iter.getNext(); // Leave this hanging as active
        iter.update(bpp[0].getSuperbucket(), new BucketId());
        iter.update(bpp[1].getSuperbucket(), new BucketId());

        iter.setDistributionBitCount(20);
        // ProgressToken doesn't change yet, since it had active buckets
        assertEquals(p.getDistributionBitCount(), 16);
        assertEquals(iter.getDistributionBitCount(), 20);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), 20);

        assertFalse(iter.hasNext());
        assertFalse(iter.isDone());
        assertTrue(iter.getBucketSource().shouldYield());
        assertEquals(p.getPendingBucketCount(), 2);
        assertEquals(p.getActiveBucketCount(), 1);

        // Finish active, triggering the consistency fixes
        iter.update(bpa.getSuperbucket(), ProgressToken.FINISHED_BUCKET);

        assertEquals(p.getDistributionBitCount(), 20);
        assertEquals(p.getPendingBucketCount(), 32);
        assertEquals(p.getActiveBucketCount(), 0);
        // Each bucket with db:16 becomes equal to 16 buckets with db:20, so
        // the bucket space position must be 16 * 6 = 96
        assertEquals(p.getBucketCursor(), 96);
        // Each finished bucket also covers less ground, so count is upped
        // accordingly
        assertEquals(p.getFinishedBucketCount(), 16 * 4);

        // Remove pending that came from the split
        // Bucket space that should be covered by the 32 buckets is [48, 80)
        // when using 20 distribution bits
        for (int i = 0; i < 32; ++i) {
            long testKey = ProgressToken.makeNthBucketKey(i + 48, 20);
            VisitorIterator.BucketProgress bp = iter.getNext();
            assertEquals(bp.getSuperbucket(), new BucketId(ProgressToken.keyToBucketId(testKey)));
            iter.update(bp.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        }
        assertEquals(p.getPendingBucketCount(), 0);
        assertEquals(p.getFinishedBucketCount(), 16 * 6);

        // Bucket source should now begin returning from bucket 20:0x6000
        assertEquals(iter.getNext().getSuperbucket(), new BucketId(20, 0x6000));
    }

    @Test
    public void testSingleBucketMerge() throws ParseException {
        int db = 2;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        // Create a range of [0, 4) superbuckets
        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        VisitorIterator.BucketProgress bp = iter.getNext();
        // Put back as pending and split it
        iter.update(bp.getSuperbucket(), new BucketId());
        p.splitPendingBucket(new BucketId(db, 0));
        assertEquals(p.getPendingBucketCount(), 2);
        // Merge both back into one node. Merge from left sibling with right present
        p.mergePendingBucket(new BucketId(db + 1, 0));
        assertEquals(p.getPendingBucketCount(), 1);
        bp = iter.getNext();
        assertEquals(bp.getSuperbucket(), new BucketId(db, 0));
    }

    @Test
    public void testRangeDistributionBitDecrease1() throws ParseException {
        int db = 16;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        VisitorIterator.DistributionRangeBucketSource src
                = (VisitorIterator.DistributionRangeBucketSource)iter.getBucketSource();

        assertTrue(src.isLosslessResetPossible());

        // For this test, have 3 finished buckets, 6 pending and 1 active
        // This gives a sibling "distribution" of FF FP PP PP PA. When all
        // active buckets have been updated, 3 merges should be triggered
        for (int i = 0; i < 3; ++i) {
            iter.update(iter.getNext().getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        }

        assertFalse(src.isLosslessResetPossible());

        VisitorIterator.BucketProgress[] bpp = new VisitorIterator.BucketProgress[6];
        for (int i = 0; i < 6; ++i) {
            bpp[i] = iter.getNext();
        }
        VisitorIterator.BucketProgress bpa = iter.getNext(); // Leave this hanging as active
        for (int i = 0; i < 6; ++i) {
            iter.update(bpp[i].getSuperbucket(), new BucketId());
        }

        assertEquals(p.getBucketCursor(), 10);

        iter.setDistributionBitCount(db - 1);
        assertEquals(iter.getDistributionBitCount(), db - 1);
        assertEquals(p.getDistributionBitCount(), db);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), db - 1);
        // The iterator is waiting patiently for all active buckets to be updated,
        // at which point it will performed the merging and actually updating the
        // progress token's distribution bit count
        assertTrue(iter.getBucketSource().shouldYield());
        assertFalse(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(p.getActiveBucketCount(), 1);
        iter.update(bpa.getSuperbucket(), new BucketId());

        assertEquals(p.getDistributionBitCount(), db - 1);
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(p.getPendingBucketCount(), 4); // 3 merges, P PP PP PP -> P P P P

        assertEquals(p.getFinishedBucketCount(), 1);
        assertEquals(p.getBucketCursor(), 5);
    }

    // Test that splitting and merging from and to the same db count gives
    // back the initial state
    @Test
    public void testRangeDistributionBitIncreaseDecrease() throws ParseException {
        int db = 16;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        VisitorIterator.DistributionRangeBucketSource src
                = (VisitorIterator.DistributionRangeBucketSource)iter.getBucketSource();

        assertTrue(src.isLosslessResetPossible());

        // "Sabotage" resetting by having at least 1 finished
        iter.update(iter.getNext().getSuperbucket(), ProgressToken.FINISHED_BUCKET);

        VisitorIterator.BucketProgress[] bpp = new VisitorIterator.BucketProgress[4];
        for (int i = 0; i < 4; ++i) {
            bpp[i] = iter.getNext();
        }
        for (int i = 0; i < 4; ++i) {
            iter.update(bpp[i].getSuperbucket(), new BucketId());
        }

        assertFalse(src.isLosslessResetPossible());

        iter.setDistributionBitCount(20);
        assertEquals(p.getDistributionBitCount(), 20);
        assertEquals(p.getPendingBucketCount(), 4 << 4);
        assertFalse(iter.getBucketSource().shouldYield());
        assertEquals(p.getBucketCursor(), 5 << 4);

        iter.setDistributionBitCount(16);

        assertEquals(p.getDistributionBitCount(), 16);
        assertEquals(p.getPendingBucketCount(), 4);
        assertFalse(iter.getBucketSource().shouldYield());
        assertEquals(p.getBucketCursor(), 5);
    }

    // Test that intermittent changes in distribution are handled properly, e.g.
    // changing from 11 -> 9 with X active and then before all those are flushed,
    // the distribution goes up to 12
    @Test
    public void testRangeDistributionBitChangeWithoutDone() throws ParseException {
        int db = 11;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        VisitorIterator.DistributionRangeBucketSource src
                = (VisitorIterator.DistributionRangeBucketSource)iter.getBucketSource();

        VisitorIterator.BucketProgress[] bpp = new VisitorIterator.BucketProgress[4];
        for (int i = 0; i < 4; ++i) {
            bpp[i] = iter.getNext();
        }
        for (int i = 0; i < 2; ++i) {
            iter.update(bpp[i].getSuperbucket(), new BucketId());
        }

        assertFalse(src.isLosslessResetPossible());

        // Now 2 pending, 2 active

        iter.setDistributionBitCount(9);
        assertEquals(p.getDistributionBitCount(), 11);
        assertEquals(p.getActiveBucketCount(), 2);
        assertEquals(p.getPendingBucketCount(), 2);
        assertTrue(iter.getBucketSource().shouldYield());
        // Update as pending, still with old count since there's 1 more active
        // with bpp[2]. Have progress so that lossless reset isn't possible
        iter.update(bpp[3].getSuperbucket(), new BucketId(15, bpp[3].getSuperbucket().getId()));

        iter.setDistributionBitCount(12);
        assertEquals(p.getActiveBucketCount(), 1);
        assertEquals(p.getPendingBucketCount(), 3);
        assertTrue(iter.getBucketSource().shouldYield());

        // Serialize before token is updated to 12 bits
        String serialized = p.toString();

        iter.update(bpp[2].getSuperbucket(), ProgressToken.FINISHED_BUCKET);

        assertEquals(p.getActiveBucketCount(), 0);
        // All active buckets are at db=11, so they should be split once each
        assertEquals(p.getPendingBucketCount(), 3 * 2);
        assertFalse(iter.getBucketSource().shouldYield());
        assertEquals(p.getFinishedBucketCount(), 2);

        // Ensure we get a consistent progress token imported
        ProgressToken p2 = new ProgressToken(serialized);
        assertEquals(p2.getDistributionBitCount(), 11); // Not yet updated

        BucketIdFactory idFactory2 = new BucketIdFactory();
        VisitorIterator iter2 = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory2, 1, p2);

        // Not yet updated, since we don't trust the initial BucketIdFactory
        assertEquals(iter2.getDistributionBitCount(), 11);
        assertEquals(p2.getDistributionBitCount(), 11);
        iter2.setDistributionBitCount(12);
        // Now it has been updated
        assertEquals(p2.getDistributionBitCount(), 12);
        assertEquals(p2.getPendingBucketCount(), 8);
        assertEquals(p2.getBucketCursor(), 8);
        assertEquals(p2.getFinishedBucketCount(), 0);
    }

    // Test a drop from 31->11 bits upon iteration start
    @Test
    public void testRangeDistributionBitInitialDrop() throws ParseException {
        int db = 31;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        VisitorIterator.BucketProgress[] bp = new VisitorIterator.BucketProgress[3];
        bp[0] = iter.getNext();
        bp[1] = iter.getNext();
        bp[2] = iter.getNext();
        iter.update(bp[2].getSuperbucket(), new BucketId());
        iter.update(bp[1].getSuperbucket(), new BucketId());
        assertEquals(p.getActiveBucketCount(), 1);

        iter.setDistributionBitCount(11);

        assertFalse(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(p.getActiveBucketCount(), 1);

        // Updating the active bucket allows the merging to take place
        iter.update(new BucketId(31, 0), new BucketId());

        assertTrue(iter.hasNext());
        assertFalse(iter.isDone());

        // All pending buckets should have been merged down to just 1 now
        // Update: now rather gets reset
        assertEquals(p.getPendingBucketCount(), 0);
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(p.getFinishedBucketCount(), 0);
        assertEquals(p.getBucketCursor(), 0);

        bp[0] = iter.getNext();
        assertEquals(bp[0].getSuperbucket(), new BucketId(11, 0));
    }

    // Similar to testRangeDistributionBitInitialDrop, but going from 1 to 11
    // This tests that doing so may be done in an optimized way rather than
    // attempting to split enough buckets to cover the entire bucket space!
    @Test
    public void testRangeDistributionLosslessReset() throws ParseException {
        int db = 1;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        VisitorIterator.DistributionRangeBucketSource src
                = (VisitorIterator.DistributionRangeBucketSource)iter.getBucketSource();

        VisitorIterator.BucketProgress[] bp = new VisitorIterator.BucketProgress[2];
        bp[0] = iter.getNext();
        bp[1] = iter.getNext();

        String serialized = p.toString();

        assertFalse(src.isLosslessResetPossible());

        iter.update(bp[1].getSuperbucket(), new BucketId());
        assertEquals(p.getActiveBucketCount(), 1);

        iter.setDistributionBitCount(11);

        assertFalse(src.isLosslessResetPossible());
        assertEquals(p.getDistributionBitCount(), 1); // Still at 1

        assertFalse(iter.hasNext());
        assertFalse(iter.isDone());
        assertEquals(p.getActiveBucketCount(), 1);

        // Updating the active bucket allows the reset to take place
        iter.update(new BucketId(1, 0), new BucketId());

        assertTrue(iter.hasNext());
        assertFalse(iter.isDone());

        // Should not be any buckets pending/active and the cursor should be
        // back at 0
        assertEquals(p.getPendingBucketCount(), 0);
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(p.getFinishedBucketCount(), 0);
        assertEquals(p.getBucketCursor(), 0);
        assertEquals(p.getDistributionBitCount(), 11);

        bp[0] = iter.getNext();
        assertEquals(bp[0].getSuperbucket(), new BucketId(11, 0));

        // Ensure resetting also works when you're importing existing
        // progress
        p = new ProgressToken(serialized);
        idFactory = new BucketIdFactory();
        iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, 1, p);

        iter.setDistributionBitCount(11);

        assertEquals(p.getPendingBucketCount(), 0);
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(p.getFinishedBucketCount(), 0);
        assertEquals(p.getBucketCursor(), 0);
        assertEquals(p.getDistributionBitCount(), 11);

        bp[0] = iter.getNext();
        assertEquals(bp[0].getSuperbucket(), new BucketId(11, 0));
    }

    @Test
    public void testExplicitDistributionBitIncrease() throws ParseException {
        int distBits = 12;

        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.user == 1234 or id.user == 6789 or id.user == 8009", idFactory, distBits, p);

        assertEquals(iter.getDistributionBitCount(), distBits);
        assertEquals(p.getDistributionBitCount(), distBits);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), distBits);

        iter.update(iter.getNext().getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        iter.setDistributionBitCount(16);

        assertEquals(iter.getDistributionBitCount(), 16);
        assertEquals(p.getDistributionBitCount(), 16);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), 16);
        // Changing dist bits for explicit source should change nothing
        assertEquals(p.getPendingBucketCount(), 2);
        assertEquals(p.getFinishedBucketCount(), 1);
        assertEquals(p.getTotalBucketCount(), 3);
    }

    @Test
    public void testExplicitDistributionBitDecrease() throws ParseException {
        int distBits = 20;

        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.user == 1234 or id.user == 6789 or id.user == 8009", idFactory, distBits, p);

        assertEquals(iter.getDistributionBitCount(), distBits);
        assertEquals(p.getDistributionBitCount(), distBits);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), distBits);

        iter.update(iter.getNext().getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        iter.setDistributionBitCount(16);

        assertEquals(iter.getDistributionBitCount(), 16);
        assertEquals(p.getDistributionBitCount(), 16);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), 16);
        // Changing dist bits for explicit source should change nothing
        assertEquals(p.getPendingBucketCount(), 2);
        assertEquals(p.getFinishedBucketCount(), 1);
        assertEquals(p.getTotalBucketCount(), 3);
    }

    @Test
    public void testExplicitDistributionImportNoTruncation() throws ParseException {
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.user == 1234 or id.user == 6789 or id.user == 8009", idFactory, 20, p);
        assertEquals(20, iter.getDistributionBitCount());
        assertEquals(20, p.getDistributionBitCount());
        assertEquals(20, iter.getBucketSource().getDistributionBitCount());

        iter.update(iter.getNext().getSuperbucket(), ProgressToken.FINISHED_BUCKET);

        // Make sure no truncation is done on import
        String serialized = p.toString();
        ProgressToken p2 = new ProgressToken(serialized);
        BucketIdFactory idFactory2 = new BucketIdFactory();
        VisitorIterator iter2 = VisitorIterator.createFromDocumentSelection(
                "id.user == 1234 or id.user == 6789 or id.user == 8009", idFactory2, 1, p2);
        assertEquals(20, iter2.getDistributionBitCount());
        assertEquals(20, p2.getDistributionBitCount());
        assertEquals(20, iter2.getBucketSource().getDistributionBitCount());
        assertEquals(2, p2.getPendingBucketCount());
        assertEquals(1, p2.getFinishedBucketCount());
        assertEquals(3, p2.getTotalBucketCount());
    }

    @Test
    public void testImportProgressWithOutdatedDistribution() throws ParseException {
        String input = "VDS bucket progress file\n" +
                "10\n" +
                "503\n" +
                "500\n" +
                "1024\n" +
                "28000000000000be:0\n" +
                "28000000000002be:0\n" +
                "28000000000001be:0\n";

        int db = 12;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken(input);
        assertEquals(10, p.getDistributionBitCount());

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, 1, p);

        iter.setDistributionBitCount(12);
        assertEquals(iter.getDistributionBitCount(), 12);
        assertEquals(p.getDistributionBitCount(), 12);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), 12);

        assertEquals(p.getTotalBucketCount(), 1 << 12);
        assertEquals(p.getFinishedBucketCount(), 500 << 2);
        assertEquals(p.getPendingBucketCount(), 3 << 2);
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(p.getBucketCursor(), 503 << 2);
        assertTrue(iter.hasNext());

        ProgressToken p2 = new ProgressToken(p.serialize());
        assertEquals(p2.getDistributionBitCount(), 12);
        assertEquals(p2.getTotalBucketCount(), 1 << 12);
        assertEquals(p2.getFinishedBucketCount(), 500 << 2);
        assertEquals(p2.getPendingBucketCount(), 3 << 2);
        assertEquals(p2.getActiveBucketCount(), 0);
        assertEquals(p2.getBucketCursor(), 503 << 2);
    }

    @Test
    public void testImportInconsistentProgressIncrease() throws ParseException {
        // Bucket progress "file" that upon time of changing from 4 to 7
        // distribution bits and writing the progress had an active bucket
        String input = "VDS bucket progress file\n" +
                "7\n" +
                "32\n" +
                "24\n" +
                "128\n" +
                "100000000000000c:0\n";
        // Now we're at 8 distribution bits
        int db = 8;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken(input);
        assertEquals(7, p.getDistributionBitCount());
        assertEquals(p.getTotalBucketCount(), 1 << 7);
        assertEquals(p.getFinishedBucketCount(), 24);
        // Not yet corrected
        assertEquals(p.getPendingBucketCount(), 1);
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(32, p.getBucketCursor());

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, 1, p);

        // Now the range handler should have corrected the progress
        // (but not messed with the distribution bit count)
        assertEquals(7, p.getDistributionBitCount());
        assertEquals(p.getPendingBucketCount(), 1 << 3);
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(24 + (1 << 3), p.getBucketCursor());

        iter.setDistributionBitCount(8);

        assertEquals(iter.getDistributionBitCount(), 8);
        assertEquals(p.getDistributionBitCount(), 8);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), 8);

        assertEquals(p.getTotalBucketCount(), 1 << 8);
        assertEquals(p.getFinishedBucketCount(), 24 << 1);
        assertEquals(p.getPendingBucketCount(), 1 << 4); // Split 4 -> 7 bits, then 7 -> 8 bits
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(p.getBucketCursor(), 24*2 + (1 << 4));
        assertTrue(iter.hasNext());
    }

    @Test
    public void testImportInconsistentProgressDecrease() throws ParseException {
        // Bucket progress "file" that upon time of changing from 4 to 7
        // distribution bits and writing the progress had an active bucket
        String input = "VDS bucket progress file\n" +
                "7\n" +
                "32\n" +
                "24\n" +
                "128\n" +
                "100000000000000c:0\n";
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken(input);

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, 1, p);

        assertEquals(iter.getDistributionBitCount(), 7);
        // Now we're at 6 distribution bits
        iter.setDistributionBitCount(6);

        assertEquals(iter.getDistributionBitCount(), 6);
        assertEquals(p.getDistributionBitCount(), 6);
        assertEquals(iter.getBucketSource().getDistributionBitCount(), 6);

        assertEquals(p.getTotalBucketCount(), 1 << 6);
        assertEquals(p.getFinishedBucketCount(), 24 >> 1);
        assertEquals(p.getPendingBucketCount(), 1 << 2); // Split 4 -> 7 bits, merge 7 -> 6 bits
        assertEquals(p.getActiveBucketCount(), 0);
        assertEquals(p.getBucketCursor(), 24/2 + (1 << 2));
        assertTrue(iter.hasNext());
    }

    @Test
    public void testEntireBucketSpaceCovered() throws ParseException {
        int db = 4;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        VisitorIterator.BucketProgress[] bpp = new VisitorIterator.BucketProgress[3];

        for (int i = 0; i < 3; ++i) {
            bpp[i] = iter.getNext();
        }
        for (int i = 0; i < 3; ++i) {
            // Must use non-zero progress or all pending will be optimized
            // away by the reset-logic
            iter.update(bpp[i].getSuperbucket(),
                    new BucketId(db + 1, bpp[i].getSuperbucket().getId()));
        }

        Set<BucketId> buckets = new TreeSet<BucketId>();
        db = 7;
        for (int i = 0; i < (1 << db); ++i) {
            buckets.add(new BucketId(db, i));
        }

        iter.setDistributionBitCount(db);
        assertEquals(p.getFinishedBucketCount(), 0);
        assertEquals(p.getPendingBucketCount(), 3 << 3);

        // Ensure all buckets are visited once and only once
        while (iter.hasNext()) {
            VisitorIterator.BucketProgress bp = iter.getNext();
            assertTrue(buckets.contains(bp.getSuperbucket()));
            buckets.remove(bp.getSuperbucket());
        }

        assertTrue(buckets.isEmpty());
    }

    @Test
    public void testExceptionOnWrongDocumentSelection() throws ParseException {
        BucketIdFactory idFactory = new BucketIdFactory();
        // Since we don't store the actual original document selection in the
        // progress files, we can't really tell whether or not a "wrong" document
        // selection has been given, so we just do a best effort by checking
        // that the number of total buckets match up and that the bucket cursor
        // isn't set for explicit sources

        // Try to pass a known document selection to an unknown docsel iterator
        boolean caughtIt = false;
        try {
            ProgressToken p = new ProgressToken("VDS bucket progress file\n16\n3\n1\n3\n"
                    + "8000000000001f49:0\n8000000000001a85:0\n");

            VisitorIterator.createFromDocumentSelection("id.group != \"yahoo.com\"", idFactory, 16, p);
        }
        catch (IllegalArgumentException e) {
            caughtIt = true;
        }
        assertTrue(caughtIt);

        // Now try it the other way around
        caughtIt = false;
        try {
            ProgressToken p = new ProgressToken("VDS bucket progress file\n" +
                    "10\n" +
                    "503\n" +
                    "500\n" +
                    "1024\n" +
                    "28000000000000be:0\n" +
                    "28000000000002be:0\n" +
                    "28000000000001be:0\n");

            VisitorIterator.createFromDocumentSelection("id.group=\"yahoo.com\" or id.user=555", idFactory, 16, p);
        }
        catch (IllegalArgumentException e) {
            caughtIt = true;
        }
        assertTrue(caughtIt);
    }

    @Test
    public void testIsBucketFinished() throws ParseException {
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();
        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, 4, p);

        assertFalse(p.isBucketFinished(new BucketId(32, 0)));
        // Finish superbucket 0x0000
        iter.update(iter.getNext().getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        assertTrue(p.isBucketFinished(new BucketId(32, 0)));
        // Cursor is 1, but bucket 0x1000 not yet returned
        assertFalse(p.isBucketFinished(new BucketId(32, 1 << 3)));
        VisitorIterator.BucketProgress bp = iter.getNext();
        // Cursor 2, 0x1000 returned but is contained in state, so not finished
        assertFalse(p.isBucketFinished(new BucketId(32, 1 << 3)));
        iter.update(bp.getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        assertTrue(p.isBucketFinished(new BucketId(32, 1 << 3)));
        // Only superbucket part is used
        assertTrue(p.isBucketFinished(new BucketId(32, 0x12345670)));  // ...0000
        assertTrue(p.isBucketFinished(new BucketId(32, 0x12345678)));  // ...1000
        assertFalse(p.isBucketFinished(new BucketId(32, 0x12345671))); // ...0001
        assertFalse(p.isBucketFinished(new BucketId(32, 0x12345679))); // ...1001
    }

    // Test that altering distribution bit count sets ProgressToken as
    // inconsistent when there are active buckets
    @Test
    public void testInconsistentState() throws ParseException {
        int db = 16;
        BucketIdFactory idFactory = new BucketIdFactory();
        ProgressToken p = new ProgressToken();

        VisitorIterator iter = VisitorIterator.createFromDocumentSelection(
                "id.group != \"yahoo.com\"", idFactory, db, p);

        // For this test, have 3 finished bucket, 2 pending and 1 active
        for (int i = 0; i < 3; ++i) {
            iter.update(iter.getNext().getSuperbucket(), ProgressToken.FINISHED_BUCKET);
        }

        VisitorIterator.BucketProgress[] bpp = new VisitorIterator.BucketProgress[2];
        bpp[0] = iter.getNext();
        bpp[1] = iter.getNext();
        VisitorIterator.BucketProgress bpa = iter.getNext(); // Leave this hanging as active
        iter.update(bpp[0].getSuperbucket(), new BucketId());
        iter.update(bpp[1].getSuperbucket(), new BucketId());

        assertFalse(p.isInconsistentState());
        iter.setDistributionBitCount(20);
        assertTrue(p.isInconsistentState());

        // Finish active, triggering the consistency fixes
        iter.update(bpa.getSuperbucket(), ProgressToken.FINISHED_BUCKET);

        assertFalse(p.isInconsistentState());
    }

    @Test
    public void testMalformedProgressFile() {
        boolean caughtIt = false;
        try {
            new ProgressToken("VDS bucket progress file\n" +
                    "10\n" +
                    "503\n" +
                    "500\n" +
                    "1024\n" +
                    "28000000000000be:0\n" +
                    "28000000000002be:");
        } catch (IllegalArgumentException e) {
            caughtIt = true;
        }
        assertTrue(caughtIt);
    }

    @Test
    public void testFailOnTooFewLinesInFile() {
        boolean caughtIt = false;
        try {
            new ProgressToken("VDS bucket progress file\n" +
                    "10\n" +
                    "503\n");
        } catch (IllegalArgumentException e) {
            caughtIt = true;
        }
        assertTrue(caughtIt);
    }

    @Test
    public void testUnknownFirstHeaderLine() {
        boolean caughtIt = false;
        try {
            new ProgressToken("Smurf Time 3000\n" +
                    "10\n" +
                    "503\n" +
                    "500\n" +
                    "1024\n" +
                    "28000000000000be:0\n" +
                    "28000000000002be:0");
        } catch (IllegalArgumentException e) {
            caughtIt = true;
        }
        assertTrue(caughtIt);
    }

    @Test
    public void testBinaryProgressSerialization() {
        String input = "VDS bucket progress file (48.828125% completed)\n" +
                "10\n" +
                "503\n" +
                "500\n" +
                "1024\n" +
                "28000000000000be:0\n" +
                "28000000000002be:0\n" +
                "28000000000001be:0\n";
        ProgressToken p = new ProgressToken(input);
        byte[] buf = p.serialize();
        ProgressToken p2 = new ProgressToken(buf);
        assertEquals(input, p2.toString());
    }

}
