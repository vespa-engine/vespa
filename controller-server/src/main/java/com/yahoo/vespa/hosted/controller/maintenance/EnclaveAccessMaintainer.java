package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

public class EnclaveAccessMaintainer extends ControllerMaintainer {

    private static final Logger logger = Logger.getLogger(EnclaveAccessMaintainer.class.getName());

    EnclaveAccessMaintainer(Controller controller, Duration interval) {
        super(controller, interval, null, Set.of(SystemName.PublicCd, SystemName.Public));
    }

    @Override
    protected double maintain() {
        try {
            return controller().serviceRegistry().enclaveAccessService().allowAccessFor(externalAccounts());
        } catch (RuntimeException e) {
            logger.log(WARNING, "Failed sharing resources with enclave", e);
            return 1.0;
        }
    }

    private Set<CloudAccount> externalAccounts() {
        Set<CloudAccount> accounts = new HashSet<>();
        for (Tenant tenant : controller().tenants().asList())
            accounts.addAll(controller().applications().accountsOf(tenant.name()));

        return accounts;
    }

}
