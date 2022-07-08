// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author jvenstad
 */
public class ApplicationMetaDataGarbageCollector extends ControllerMaintainer {

    private static final Logger log = Logger.getLogger(ApplicationMetaDataGarbageCollector.class.getName());

    private final Duration timeToLive;

    public ApplicationMetaDataGarbageCollector(Controller controller, Duration interval) {
        super(controller, interval);
        this.timeToLive = controller.system().isCd() ? Duration.ofDays(7) : Duration.ofDays(365);
    }

    @Override
    protected double maintain() {
        try {
            controller().applications().applicationStore().pruneMeta(controller().clock().instant().minus(timeToLive));
            return 1.0;
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Exception pruning old application meta data", e);
            return 0.0;
        }
    }

}
