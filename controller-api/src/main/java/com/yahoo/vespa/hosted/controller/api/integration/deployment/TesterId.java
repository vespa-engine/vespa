// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.ApplicationId;

/**
 * Holds an application ID for a tester application.
 *
 * @author jonmv
 */
public class TesterId {

    public static final String suffix = "-t";

    private final ApplicationId id;

    private TesterId(ApplicationId id) {
        this.id = id;
    }

    /** Creates a new TesterId for a tester of the given application. */
    public static TesterId of(ApplicationId id) {
        return new TesterId(ApplicationId.from(id.tenant().value(),
                                               id.application().value(),
                                               id.instance().value() + suffix));
    }

    /** Returns the id of this tester application. */
    public ApplicationId id() {
        return id;
    }

}
