package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.CloudAccount;

import java.util.Set;
import java.util.TreeSet;

/**
 * @author jonmv
 */
public class MockEnclaveAccessService implements EnclaveAccessService {

    private volatile Set<CloudAccount> currentAccounts = new TreeSet<>();

    public Set<CloudAccount> currentAccounts() { return currentAccounts; }

    @Override
    public double allowAccessFor(Set<CloudAccount> accounts) {
        currentAccounts = new TreeSet<>(accounts);
        return 1;
    }

}
