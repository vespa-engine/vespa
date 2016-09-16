// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.legacy;

import java.util.List;

/**
 * Value class used to convert to/from JSON.
 *
 * @author mortent
 */
class ProvisionStatus {

    public int requiredNodes;
    public List<HostInfo> decomissionNodes;
    public List<HostInfo> failedNodes;

}
