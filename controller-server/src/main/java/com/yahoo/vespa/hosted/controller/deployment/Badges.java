package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;

import java.net.URI;
import java.util.List;

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
    URI historic(ApplicationId id, List<Run> runs) {
        StringBuilder path = new StringBuilder(id + ";" + dark);

        if ( ! runs.isEmpty()) {
            Run lastCompleted = runs.get(runs.size() - 1);
            if (runs.size() > 1 && !lastCompleted.hasEnded())
                lastCompleted = runs.get(runs.size() - 2);

            path.append("/").append(lastCompleted.id().type().jobName()).append(";").append(colorOf(lastCompleted));
            for (Run run : runs)
                path.append("/%20;").append(colorOf(run)).append(";s%7B").append(white).append("%7D");
        }

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
