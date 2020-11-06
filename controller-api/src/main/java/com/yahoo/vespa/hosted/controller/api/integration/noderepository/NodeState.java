// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

public enum NodeState {

    provisioned,
    ready,
    reserved,
    active,
    inactive,
    dirty,
    failed,
    parked,
    deprovisioned,
    breakfixed

}
