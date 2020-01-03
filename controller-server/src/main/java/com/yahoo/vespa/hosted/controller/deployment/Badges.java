// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * URLs for deployment job badges using <a href="https://github.com/yahoo/badge-up">badge-up</a>.
 *
 * @author jonmv
 */
class Badges {

    static final String dark   = "555555",
                        blue   = "4477DD",
                        red    = "DD4444",
                        purple = "AA11CC",
                        yellow = "DDAA11",
                        white  = "FFFFFF";

    private final URI badgeApi;

    Badges(URI badgeApi) {
        this.badgeApi = badgeApi;
    }

    /** Returns a URI which gives a history badge for the given runs. */
    URI historic(ApplicationId id, Optional<Run> lastCompleted, List<Run> runs) {
        StringBuilder path = new StringBuilder(id + ";" + dark);

        lastCompleted.ifPresent(last -> path.append("/").append(last.id().type().jobName()).append(";").append(colorOf(last)));
        for (Run run : runs)
            path.append("/%20;").append(colorOf(run)).append(";s%7B").append(white).append("%7D");

        return badgeApi.resolve(path.toString());
    }

    /** Returns a URI which gives an overview badge for the given runs. */
    URI overview(ApplicationId id, List<Run> runs) {
        StringBuilder path = new StringBuilder(id + ";" + dark);
        for (Run run : runs)
            path.append("/").append(run.id().type().jobName()).append(";").append(colorOf(run));

        return badgeApi.resolve(path.toString());
    }

    private static String colorOf(Run run) {
        switch (run.status()) {
            case success: return blue;
            case running: return purple;
            case aborted: return yellow;
            default:      return red;
        }
    }

}
