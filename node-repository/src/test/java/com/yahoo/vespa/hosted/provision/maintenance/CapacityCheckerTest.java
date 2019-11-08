package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mgimle
 */
public class CapacityCheckerTest {
    private CapacityCheckerTester tester;

    @Before
    public void setup() {
        tester = new CapacityCheckerTester();
    }

    @Test
    public void testWithRealData() throws IOException {
        String path = "./src/test/resources/zookeeper_dump.json";

        tester.cleanRepository();
        tester.restoreNodeRepositoryFromJsonFile(Paths.get(path));
        var failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        assertTrue(tester.nodeRepository.getNodes(NodeType.host).containsAll(failurePath.get().hostsCausingFailure));
    }

    @Test
    public void findsMoveFromSlowToFastDisk() {
        tester.createNodes(1, 1, List.of(new NodeResources(1, 10, 100, 1).withDiskSpeed(NodeResources.DiskSpeed.any)),
                1, new NodeResources(1, 10, 100, 1).withDiskSpeed(NodeResources.DiskSpeed.slow), 10,
                1, new NodeResources(1, 10, 100, 1).withDiskSpeed(NodeResources.DiskSpeed.fast), 10);

        var failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());

        int worstCaseHostLoss = failurePath.get().hostsCausingFailure.size();
        int spareHostCapacity = worstCaseHostLoss - 1;

        assertEquals("did not find move from slow to fast disk", 1, spareHostCapacity);
    }

    @Test
    public void findsMoveFromFastToSlowDisk() {
        tester.createNodes(1, 1, List.of(new NodeResources(1, 10, 100, 1).withDiskSpeed(NodeResources.DiskSpeed.any)),
                1, new NodeResources(1, 10, 100, 1).withDiskSpeed(NodeResources.DiskSpeed.fast), 10,
                1, new NodeResources(1, 10, 100, 1).withDiskSpeed(NodeResources.DiskSpeed.slow), 10);

        var failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());

        int worstCaseHostLoss = failurePath.get().hostsCausingFailure.size();
        int spareHostCapacity = worstCaseHostLoss - 1;

        assertEquals("did not find move from fast to slow disk", 1, spareHostCapacity);
    }

    @Test
    public void testOvercommittedHosts() {
        tester.createNodes(7, 4,
               10, new NodeResources(-1, 10, 100, 1), 10,
                0, new NodeResources(1, 10, 100, 1), 10);
        int overcommittedHosts = tester.capacityChecker.findOvercommittedHosts().size();
        assertEquals(tester.nodeRepository.getNodes(NodeType.host).size(), overcommittedHosts);
    }

    @Test
    public void testEdgeCaseFailurePaths() {
        tester.createNodes(1, 1,
                0, new NodeResources(1, 10, 100, 1), 10,
                0, new NodeResources(1, 10, 100, 1), 10);
        var failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertFalse("Computing worst case host loss with no hosts should return an empty optional.", failurePath.isPresent());

        // Odd edge case that should never be able to occur in prod
        tester.createNodes(1, 10,
                10, new NodeResources(10, 1000, 10000, 1), 100,
                1, new NodeResources(10, 1000, 10000, 1), 100);
        failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        assertTrue("Computing worst case host loss if all hosts have to be removed should result in an non-empty failureReason with empty nodes.",
                failurePath.get().failureReason.tenant.isEmpty() && failurePath.get().failureReason.host.isEmpty());
        assertEquals(tester.nodeRepository.getNodes(NodeType.host).size(), failurePath.get().hostsCausingFailure.size());

        tester.createNodes(3, 30,
                10, new NodeResources(0, 0, 10000, 1), 1000,
                0, new NodeResources(0, 0, 0, 0), 0);
        failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        if (failurePath.get().failureReason.tenant.isPresent()) {
            var failureReasons = failurePath.get().failureReason.allocationFailures;
            assertEquals("When there are multiple lacking resources, all failures are multipleReasonFailures",
                    failureReasons.size(), failureReasons.multipleReasonFailures().size());
            assertEquals(0, failureReasons.singularReasonFailures().size());
        } else fail();
    }

    @Test
    public void testIpFailurePaths() {
        tester.createNodes(1, 10,
                10, new NodeResources(10, 1000, 10000, 1), 1,
                10, new NodeResources(10, 1000, 10000, 1), 1);
        var failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        if (failurePath.get().failureReason.tenant.isPresent()) {
            var failureReasons = failurePath.get().failureReason.allocationFailures;
            assertEquals("All failures should be due to hosts having a lack of available ip addresses.",
                    failureReasons.singularReasonFailures().insufficientAvailableIps(), failureReasons.size());
        } else fail();

    }

    @Test
    public void testNodeResourceFailurePaths() {
        tester.createNodes(1, 10,
                10, new NodeResources(1, 100, 1000, 1), 100,
                10, new NodeResources(0, 100, 1000, 1), 100);
        var failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        if (failurePath.get().failureReason.tenant.isPresent()) {
            var failureReasons = failurePath.get().failureReason.allocationFailures;
            assertEquals("All failures should be due to hosts lacking cpu cores.",
                    failureReasons.singularReasonFailures().insufficientVcpu(), failureReasons.size());
        } else fail();

        tester.createNodes(1, 10,
                10, new NodeResources(10, 1, 1000, 1), 100,
                10, new NodeResources(10, 0, 1000, 1), 100);
        failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        if (failurePath.get().failureReason.tenant.isPresent()) {
            var failureReasons = failurePath.get().failureReason.allocationFailures;
            assertEquals("All failures should be due to hosts lacking memory.",
                    failureReasons.singularReasonFailures().insufficientMemoryGb(), failureReasons.size());
        } else fail();

        tester.createNodes(1, 10,
                10, new NodeResources(10, 100, 10, 1), 100,
                10, new NodeResources(10, 100, 0, 1), 100);
        failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        if (failurePath.get().failureReason.tenant.isPresent()) {
            var failureReasons = failurePath.get().failureReason.allocationFailures;
            assertEquals("All failures should be due to hosts lacking disk space.",
                    failureReasons.singularReasonFailures().insufficientDiskGb(), failureReasons.size());
        } else fail();

        int emptyHostsWithSlowDisk = 10;
        tester.createNodes(1, 10, List.of(new NodeResources(1, 10, 100, 1)),
                10, new NodeResources(0, 0, 0, 0), 100,
                10, new NodeResources(10, 1000, 10000, 1, NodeResources.DiskSpeed.slow), 100);
        failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        if (failurePath.get().failureReason.tenant.isPresent()) {
            var failureReasons = failurePath.get().failureReason.allocationFailures;
            assertEquals("All empty hosts should be invalid due to having incompatible disk speed.",
                    failureReasons.singularReasonFailures().incompatibleDiskSpeed(), emptyHostsWithSlowDisk);
        } else fail();

    }


    @Test
    public void testParentHostPolicyIntegrityFailurePaths() {
        tester.createNodes(1, 1,
                10, new NodeResources(1, 100, 1000, 1), 100,
                10, new NodeResources(10, 1000, 10000, 1), 100);
        var failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        if (failurePath.get().failureReason.tenant.isPresent()) {
            var failureReasons = failurePath.get().failureReason.allocationFailures;
            assertEquals("With only one type of tenant, all failures should be due to violation of the parent host policy.",
                    failureReasons.singularReasonFailures().violatesParentHostPolicy(), failureReasons.size());
        } else fail();

        tester.createNodes(1, 2,
                10, new NodeResources(10, 100, 1000, 1), 1,
                0, new NodeResources(0, 0, 0, 0), 0);
        failurePath = tester.capacityChecker.worstCaseHostLossLeadingToFailure();
        assertTrue(failurePath.isPresent());
        if (failurePath.get().failureReason.tenant.isPresent()) {
            var failureReasons = failurePath.get().failureReason.allocationFailures;
            assertNotEquals("Fewer distinct children than hosts should result in some parent host policy violations.",
                    failureReasons.size(), failureReasons.singularReasonFailures().violatesParentHostPolicy());
            assertNotEquals(0, failureReasons.singularReasonFailures().violatesParentHostPolicy());
        } else fail();
    }
}


