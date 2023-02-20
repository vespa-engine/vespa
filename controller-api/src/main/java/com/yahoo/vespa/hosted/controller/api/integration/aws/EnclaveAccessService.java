package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.CloudAccount;

import java.util.Set;

/**
 * @author jonmv
 */
public interface EnclaveAccessService {

    /**
     * Ensures the given enclave accounts have access to resources they require to function.
     * @return the degree to which the run was successful - a number between 0 (no success), to 1 (complete success)
     */
    double allowAccessFor(Set<CloudAccount> accounts);

}
