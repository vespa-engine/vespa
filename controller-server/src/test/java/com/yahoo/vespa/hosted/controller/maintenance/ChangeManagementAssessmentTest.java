package com.yahoo.vespa.hosted.controller.maintenance;


import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChangeManagementAssessmentTest{

    @Test
    public void empty_input_variations() {
        ZoneId zone = ZoneId.from("prod", "eu-trd");
        List<String> hostNames = new ArrayList<>();
        List<NodeRepositoryNode> allNodesInZone = new ArrayList<>();

        // Both zone and hostnames are empty
        List<ChangeManagementAssessment.Assessment> assessments
                = ChangeManagementAssessment.assessmentInner(hostNames, allNodesInZone, zone);
        Assert.assertEquals(0, assessments.size());
    }
}