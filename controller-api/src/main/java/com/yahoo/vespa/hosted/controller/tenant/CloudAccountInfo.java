// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import com.yahoo.component.Version;
import com.yahoo.config.provision.CloudAccount;

import java.util.Objects;

/**
 * @author freva
 */
public record CloudAccountInfo(CloudAccount cloudAccount, Version templateVersion) {

    public CloudAccountInfo {
        Objects.requireNonNull(cloudAccount, "cloudAccount must be non-null");
        Objects.requireNonNull(templateVersion, "templateVersion must be non-null");
    }

}
