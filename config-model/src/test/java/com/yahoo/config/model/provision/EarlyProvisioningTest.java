// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.test.VespaModelTester;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies which clusters are collected for early provisioning, see
 * {@link com.yahoo.config.model.builder.xml.ConfigModelBuilder#collectAllocationRequests},
 * and that they are equal to those prepared.
 *
 * @author hakonhall
 */
public class EarlyProvisioningTest {

    @Test
    void containerAndContentWithImplicitAdmin() {
        assertPrepares("""
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
                       """,
                       10,
                       "(container.foo)", "admin.logserver", "content.bar", "admin.cluster-controllers");
    }

    @Test
    void clusterControllersAreCollectedOnceWithSeveralContentClusters() {
        assertPrepares("""
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
                         <content version='1.0' id='baz'>
                           <redundancy>2</redundancy>
                           <documents>
                             <document type='type1' mode='index'/>
                           </documents>
                           <nodes count='2'/>
                         </content>
                       </services>
                       """,
                       10,
                       "(container.foo)", "admin.logserver", "content.bar", "admin.cluster-controllers", "content.baz");
    }

    @Test
    void dedicatedSlobroksAndLogserver() {
        assertPrepares("""
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
                       """,
                       10,
                       "(container.foo)", "admin.slobroks", "admin.logserver");
    }

    /**
     * Builds a hosted model of the given services with early provisioning, and verifies which clusters are
     * passed to earlyPrepare() and to prepare(), and that each cluster passed to both is equal in both.
     *
     * <p>TODO: Remove the parentheses as collectAllocationRequests() is implemented for the remaining builders.
     * Once none are left, remove the syntax and compare earlyPrepareRequests() to prepareRequests() directly.</p>
     *
     * @param expectedClusters the clusters passed to prepare() during the build, in order, each as "type.id".
     *                         Those passed to earlyPrepare() too must be passed to it in the same order.
     *                         A cluster in parentheses, "(type.id)", is not passed to earlyPrepare().
     */
    private static void assertPrepares(String services, int hosts, String... expectedClusters) {
        var tester = new VespaModelTester();
        tester.setModelProperties(new TestProperties().setEarlyProvisioning(true));
        tester.useDedicatedNodeForLogserver(true); // the default of ModelContext.Properties, unlike the tester's
        tester.addHosts(hosts);
        var endpoints = Set.of(new ContainerEndpoint("foo", ApplicationClusterEndpoint.Scope.zone, List.of("foo.example.invalid")));
        tester.createModel(services, true, new DeployState.Builder().endpoints(endpoints));

        assertTrue(tester.earlyPrepareHasBeenCalled(), "Expected exactly one earlyPrepare() call");
        List<ClusterSpec> earlyRequests = tester.earlyPrepareRequests();
        List<ClusterSpec> prepareRequests = tester.prepareRequests();

        var expected = List.of(expectedClusters);
        assertEquals(expected.stream().map(EarlyProvisioningTest::withoutParentheses).toList(),
                     typesAndIdsOf(prepareRequests),
                     "Clusters passed to prepare()");
        assertEquals(expected.stream().filter(cluster -> ! isInParentheses(cluster)).toList(),
                     typesAndIdsOf(earlyRequests),
                     "Clusters passed to earlyPrepare()");

        // The type and ID identifies a cluster, but the whole cluster must be equal
        var earlyIterator = earlyRequests.iterator();
        for (int i = 0; i < expected.size(); ++i) {
            if (isInParentheses(expected.get(i))) continue;
            ClusterSpec prepared = prepareRequests.get(i);
            ClusterSpec early = earlyIterator.next();
            assertEquals(prepared, early, expected.get(i) + " passed to earlyPrepare() differs from that passed to prepare()");
        }
    }

    private static boolean isInParentheses(String cluster) {
        return cluster.startsWith("(") && cluster.endsWith(")");
    }

    private static String withoutParentheses(String cluster) {
        return isInParentheses(cluster) ? cluster.substring(1, cluster.length() - 1) : cluster;
    }

    private static List<String> typesAndIdsOf(List<ClusterSpec> clusters) {
        return clusters.stream().map(cluster -> cluster.type() + "." + cluster.id().value()).toList();
    }

}
