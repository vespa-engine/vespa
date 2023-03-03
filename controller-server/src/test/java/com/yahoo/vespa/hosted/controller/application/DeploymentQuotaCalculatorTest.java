// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.ApplicationData;
import com.yahoo.vespa.hosted.controller.deployment.RevisionHistory;
import com.yahoo.vespa.hosted.controller.metric.ApplicationMetrics;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeploymentQuotaCalculatorTest {

    @Test
    void quota_is_divided_among_prod_instances() {
        Quota calculated = DeploymentQuotaCalculator.calculate(Quota.unlimited().withBudget(10), List.of(), ApplicationId.defaultId(), ZoneId.defaultId(),
                DeploymentSpec.fromXml(
                        """
                        <deployment version='1.0'>
                          <instance id='instance1'>\s
                            <test />
                            <staging />
                            <prod>
                              <region active="true">us-east-1</region>
                              <region active="false">us-west-1</region>
                            </prod>
                          </instance>
                          <instance id='instance2'>
                            <perf/>
                            <dev/>
                            <prod>
                              <region active="true">us-north-1</region>
                            </prod>
                          </instance>
                        </deployment>"""));
        assertEquals(10d / 3, calculated.budget().orElseThrow().doubleValue(), 1e-5);
    }

    @Test
    void quota_is_divided_among_prod_and_manual_instances() {

        var existing_dev_deployment = new Application(TenantAndApplicationId.from(ApplicationId.defaultId()), Instant.EPOCH, DeploymentSpec.empty, ValidationOverrides.empty, Optional.empty(),
                Optional.empty(), Optional.empty(), OptionalInt.empty(), new ApplicationMetrics(1, 1), Set.of(), OptionalLong.empty(), RevisionHistory.empty(),
                List.of(new Instance(ApplicationId.defaultId()).withNewDeployment(ZoneId.from(Environment.dev, RegionName.defaultName()),
                                                                                         RevisionId.forProduction(1), Version.emptyVersion, Instant.EPOCH, Map.of(), QuotaUsage.create(0.53d), CloudAccount.empty)));

        Quota calculated = DeploymentQuotaCalculator.calculate(Quota.unlimited().withBudget(2), List.of(existing_dev_deployment), ApplicationId.defaultId(), ZoneId.defaultId(),
                DeploymentSpec.fromXml(
                        """
                        <deployment version='1.0'>
                          <instance id='default'>\s
                            <test />
                            <staging />
                            <prod>
                              <region active="true">us-east-1</region>
                              <region active="false">us-west-1</region>
                              <region active="true">us-north-1</region>
                              <region active="true">us-south-1</region>
                            </prod>
                          </instance>
                        </deployment>"""));
        assertEquals((2d - 0.53d) / 4d, calculated.budget().orElseThrow().doubleValue(), 1e-5);
    }

    @Test
    void unlimited_quota_remains_unlimited() {
        Quota calculated = DeploymentQuotaCalculator.calculate(Quota.unlimited(), List.of(), ApplicationId.defaultId(), ZoneId.defaultId(), DeploymentSpec.empty);
        assertTrue(calculated.isUnlimited());
    }

    @Test
    void zero_quota_remains_zero() {
        Quota calculated = DeploymentQuotaCalculator.calculate(Quota.zero(), List.of(), ApplicationId.defaultId(), ZoneId.defaultId(), DeploymentSpec.empty);
        assertEquals(calculated.budget().orElseThrow().doubleValue(), 0, 1e-5);
    }

    @Test
    void using_highest_resource_use() throws Exception {
        var content = new String(Files.readAllBytes(Paths.get("src/test/java/com/yahoo/vespa/hosted/controller/application/response/application.json")));
        var mapper = new ObjectMapper();
        var application = mapper.readValue(content, ApplicationData.class).toApplication();
        var usage = DeploymentQuotaCalculator.calculateQuotaUsage(application);
        assertEquals(1.312, usage.rate(), 0.001);
    }

    @Test
    void tenant_quota_in_pipeline() {
        var tenantQuota = Quota.unlimited().withBudget(42);
        var calculated = DeploymentQuotaCalculator.calculate(tenantQuota, List.of(), ApplicationId.defaultId(), ZoneId.from("test", "apac1"), DeploymentSpec.empty);
        assertEquals(tenantQuota, calculated);
    }

}
