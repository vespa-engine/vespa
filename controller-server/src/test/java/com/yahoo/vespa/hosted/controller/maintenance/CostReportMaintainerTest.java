package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.MaintenanceJobList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeList;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;

public class CostReportMaintainerTest {

    @Test
    public void maintain() {
        ControllerTester tester = new ControllerTester();
        CostReportMaintainer maintainer = new CostReportMaintainer(tester.controller(), Duration.ofDays(1), new JobControl(tester.curator()), new  NodeRepoMock());
        maintainer.maintain();
    }

    class NodeRepoMock implements NodeRepositoryClientInterface {

        @Override
        public void addNodes(ZoneId zone, Collection<NodeRepositoryNode> nodes) throws IOException {

        }

        @Override
        public NodeRepositoryNode getNode(ZoneId zone, String hostname) throws IOException {
            return null;
        }

        @Override
        public void deleteNode(ZoneId zone, String hostname) throws IOException {

        }

        @Override
        public NodeList listNodes(ZoneId zone, boolean recursive) throws IOException {
            return null;
        }

        @Override
        public NodeList listNodes(ZoneId zone, String tenant, String applicationId, String instance) throws IOException {
            return null;
        }

        @Override
        public String resetFailureInformation(ZoneId zone, String nodename) throws IOException {
            return null;
        }

        @Override
        public String restart(ZoneId zone, String nodename) throws IOException {
            return null;
        }

        @Override
        public String reboot(ZoneId zone, String nodename) throws IOException {
            return null;
        }

        @Override
        public String cancelReboot(ZoneId zone, String nodename) throws IOException {
            return null;
        }

        @Override
        public String wantTo(ZoneId zone, String nodename, WantTo... actions) throws IOException {
            return null;
        }

        @Override
        public String cancelRestart(ZoneId zone, String nodename) throws IOException {
            return null;
        }

        @Override
        public String setHardwareFailureDescription(ZoneId zone, String nodename, String hardwareFailureDescription) throws IOException {
            return null;
        }

        @Override
        public void setState(ZoneId zone, NodeState nodeState, String nodename) throws IOException {

        }

        @Override
        public String enableMaintenanceJob(ZoneId zone, String jobName) throws IOException {
            return null;
        }

        @Override
        public String disableMaintenanceJob(ZoneId zone, String jobName) throws IOException {
            return null;
        }

        @Override
        public MaintenanceJobList listMaintenanceJobs(ZoneId zone) throws IOException {
            return null;
        }
    }
}