package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.CloudAccount;

import java.util.Set;

/**
 * @author jonmv
 */
public interface EnclaveAccessService {

    /** Ensures the given enclave accounts have access to resources they require to function. */
    void allowAccessFor(Set<CloudAccount> accounts);

}
