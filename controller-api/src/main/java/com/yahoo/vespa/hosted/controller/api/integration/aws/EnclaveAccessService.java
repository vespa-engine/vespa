package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.CloudAccount;

import java.util.Set;

/**
 * @author jonmv
 */
public interface EnclaveAccessService {

    /** Makes the current AMIs available to the given accounts. */
    void allowAccessFor(Set<CloudAccount> accounts);

}
