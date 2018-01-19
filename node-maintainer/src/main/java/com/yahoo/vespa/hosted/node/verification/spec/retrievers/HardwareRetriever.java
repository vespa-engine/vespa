// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

/**
 * @author olaaun
 * @author sgrostad
 */
interface HardwareRetriever {

    /**
     * Should retrieve spec from some part of the hardware, and store the result in hardwareinfo instance passed to class
     */
    void updateInfo();

}
