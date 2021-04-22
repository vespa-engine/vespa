// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.api.Quota;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author ogronnesby
 */
public class QuotaValidatorTest {

    private final Zone publicZone = new Zone(SystemName.Public, Environment.prod, RegionName.from("foo"));
    private final Zone publicCdZone = new Zone(SystemName.PublicCd, Environment.prod, RegionName.from("foo"));
    private final Quota quota = Quota.unlimited().withClusterSize(10).withBudget(BigDecimal.valueOf(1));

    @Test
    public void test_deploy_under_quota() {
        var tester = new ValidationTester(5, false, new TestProperties().setHostedVespa(true).setQuota(quota).setZone(publicZone));
        tester.deploy(null, getServices("testCluster", 5), Environment.prod, null);
    }

    @Test
    public void test_deploy_above_quota_clustersize() {
        var tester = new ValidationTester(11, false, new TestProperties().setHostedVespa(true).setQuota(quota).setZone(publicZone));
        try {
            tester.deploy(null, getServices("testCluster", 11), Environment.prod, null);
            fail();
        } catch (RuntimeException e) {
            assertEquals("Clusters testCluster exceeded max cluster size of 10", e.getMessage());
        }
    }

    @Test
    public void test_deploy_above_quota_budget() {
        var tester = new ValidationTester(10, false, new TestProperties().setHostedVespa(true).setQuota(quota).setZone(publicZone));
        try {
            tester.deploy(null, getServices("testCluster", 10), Environment.prod, null);
            fail();
        } catch (RuntimeException e) {
            assertEquals("Please free up some capacity! This deployment's quota use ($-.--) exceeds reserved quota ($-.--)!",
                    ValidationTester.censorNumbers(e.getMessage()));
        }
    }

    @Test
    public void test_deploy_above_quota_budget_in_publiccd() {
        var tester = new ValidationTester(10, false, new TestProperties().setHostedVespa(true).setQuota(quota).setZone(publicCdZone));
        try {
            tester.deploy(null, getServices("testCluster", 10), Environment.prod, null);
            fail();
        } catch (RuntimeException e) {
            assertEquals("publiccd: Please free up some capacity! This deployment's quota use ($-.--) exceeds reserved quota ($-.--)!",
                    ValidationTester.censorNumbers(e.getMessage()));
        }
    }

    @Test
    public void test_deploy_with_negative_budget() {
        var quota = Quota.unlimited().withBudget(BigDecimal.valueOf(-1));
        var tester = new ValidationTester(10, false, new TestProperties().setHostedVespa(true).setQuota(quota).setZone(publicZone));
        try {
            tester.deploy(null, getServices("testCluster", 10), Environment.prod, null);
            fail();
        } catch (RuntimeException e) {
            assertEquals("Please free up some capacity! This deployment's quota use is ($-.--) and reserved quota is below zero! ($--.--)",
                    ValidationTester.censorNumbers(e.getMessage()));
        }
    }

    private static String getServices(String contentClusterId, int nodeCount) {
        return "<services version='1.0'>" +
                "  <content id='" + contentClusterId + "' version='1.0'>" +
                "    <redundancy>1</redundancy>" +
                "    <engine>" +
                "    <proton/>" +
                "    </engine>" +
                "    <documents>" +
                "      <document type='music' mode='index'/>" +
                "    </documents>" +
                "    <nodes count='" + nodeCount + "'>" +
                "      <resources vcpu=\"[0.5, 1]\" memory=\"[1Gb, 3Gb]\" disk=\"[1Gb, 9Gb]\"/>\n" +
                "    </nodes>" +
                "   </content>" +
                "</services>";
    }


}
