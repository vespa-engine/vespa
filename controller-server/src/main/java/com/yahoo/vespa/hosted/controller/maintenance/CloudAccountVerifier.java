// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.CloudAccountInfo;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * Verifies the cloud accounts that may be used by a given user have applied the enclave template
 * and extracts the version of the applied template.
 *
 * All maintainers that operate on external cloud accounts should use the list on the Tenant instance
 * maintained by this class rather than the cloud-accounts feature flag.
 *
 * The template version can be used to determine if new features can be enabled for the cloud account.
 *
 * @author freva
 */
public class CloudAccountVerifier extends ControllerMaintainer {

    private static final Logger logger = Logger.getLogger(CloudAccountVerifier.class.getName());

    CloudAccountVerifier(Controller controller, Duration interval) {
        super(controller, interval, null, Set.of(SystemName.PublicCd, SystemName.Public));
    }

    @Override
    protected double maintain() {
        int attempts = 0, failures = 0;
        for (Tenant tenant : controller().tenants().asList()) {
            try {
                attempts++;
                List<CloudAccountInfo> cloudAccountInfos = controller().applications().accountsOf(tenant.name()).stream()
                        .flatMap(account -> controller().serviceRegistry()
                                .archiveService()
                                .getEnclaveTemplateVersion(account)
                                .map(version -> new CloudAccountInfo(account, version))
                                .stream())
                        .toList();
                controller().tenants().updateCloudAccounts(tenant.name(), cloudAccountInfos);
            } catch (RuntimeException e) {
                logger.log(WARNING, "Failed to verify cloud accounts for tenant " + tenant.name(), e);
                failures++;
            }
        }
        return asSuccessFactorDeviation(attempts, failures);
    }
}
