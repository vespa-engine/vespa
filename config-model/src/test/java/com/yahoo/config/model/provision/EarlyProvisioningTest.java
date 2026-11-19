// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.test.VespaModelTester;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the clusters collected for early provisioning, see
 * {@link com.yahoo.config.model.builder.xml.ConfigModelBuilder#collectAllocationRequests},
 * are equal to those prepared.
 *
 * @author hakonhall
 */
public class EarlyProvisioningTest {

    @Test
    void containerAndContentWithImplicitAdmin() {
        String services = """
                          <?xml version='1.0' encoding='utf-8' ?>
                          <services>
                            <container version='1.0' id='foo'>
                              <nodes count='2'/>
                            </container>
                            <content version='1.0' id='bar'>
                              <redundancy>2</redundancy>
                              <documents>
                                <document type='type1' mode='index'/>
                              </documents>
                              <nodes count='2'/>
                            </content>
                          </services>
                          """;

        // TODO: Remove the cluster IDs below as collectAllocationRequests() is implemented for their builders
        assertEarlyProvisioning(services, 10, "bar", "cluster-controllers", "foo");
    }

    @Test
    void dedicatedSlobroksAndLogserver() {
        String services = """
                          <?xml version='1.0' encoding='utf-8' ?>
                          <services>
                            <admin version='4.0'>
                              <slobroks>
                                <nodes count='3' dedicated='true'/>
                              </slobroks>
                              <logservers>
                                <nodes count='1' dedicated='true'/>
                              </logservers>
                            </admin>
                            <container version='1.0' id='foo'>
                              <nodes count='2'/>
                            </container>
                          </services>
                          """;

        // TODO: Remove the cluster IDs below as collectAllocationRequests() is implemented for their builders
        assertEarlyProvisioning(services, 10, "foo");
    }

    /**
     * Builds a hosted model of the given services with early provisioning, and verifies that each cluster
     * passed to earlyPrepare() is also passed to prepare() during the build, in the same order.
     *
     * @param notYetCollected the IDs of the clusters that are passed to prepare(), but not (yet) to earlyPrepare(),
     *                        in sorted order
     */
    private static void assertEarlyProvisioning(String services, int hosts, String... notYetCollected) {
        var tester = new VespaModelTester();
        tester.setModelProperties(new TestProperties().setEarlyProvisioning(true));
        tester.useDedicatedNodeForLogserver(true); // the default of ModelContext.Properties, unlike the tester's
        tester.addHosts(hosts);
        var endpoints = Set.of(new ContainerEndpoint("foo", ApplicationClusterEndpoint.Scope.zone, List.of("foo.example.invalid")));
        tester.createModel(services, true, new DeployState.Builder().endpoints(endpoints));

        assertTrue(tester.earlyPrepareHasBeenCalled(), "Expected exactly one earlyPrepare() call");
        List<ClusterSpec> earlyRequests = tester.earlyPrepareRequests();
        assertFalse(earlyRequests.isEmpty(), "No clusters were passed to earlyPrepare()");

        var unmatched = new ArrayList<ClusterSpec>();
        int earlyIndex = 0;
        for (ClusterSpec prepared : tester.prepareRequests()) {
            if (earlyIndex < earlyRequests.size() && prepared.equals(earlyRequests.get(earlyIndex)))
                ++earlyIndex;
            else
                unmatched.add(prepared);
        }
        assertEquals(earlyRequests.size(), earlyIndex,
                     "Early requests must occur in prepare() order: early=" + earlyRequests + ", prepare=" + tester.prepareRequests());

        // TODO: Once this is empty for all tests, replace with assertEquals(tester.prepareRequests(), tester.earlyPrepareRequests())
        assertEquals(List.of(notYetCollected),
                     unmatched.stream().map(cluster -> cluster.id().value()).sorted().toList());
    }

}
