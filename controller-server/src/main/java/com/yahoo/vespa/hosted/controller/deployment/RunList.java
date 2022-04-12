// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.Collection;
import java.util.List;

/**
 * List for filtering deployment job {@link Run}s.
 *
 * @author jonmv
 */
public class RunList extends AbstractFilteringList<Run, RunList> {

    private RunList(Collection<? extends Run> items, boolean negate) {
        super(items, negate, RunList::new);
    }

    public static RunList from(Collection<? extends Run> runs) {
        return new RunList(runs, false);
    }

    public static RunList from(JobStatus job) {
        return from(job.runs().descendingMap().values());
    }

    /** Returns the jobs with runs matching the given versions — targets only for system test, everything present otherwise. */
    public RunList on(Versions versions) {
        return matching(run -> matchingVersions(run, versions));
    }

    /** Returns the runs with status among the given. */
    public RunList status(RunStatus... status) {
        return matching(run -> List.of(status).contains(run.status()));
    }

    private static boolean matchingVersions(Run run, Versions versions) {
        return    versions.targetsMatch(run.versions())
               && (versions.sourcesMatchIfPresent(run.versions()) || run.id().type().isSystemTest());
    }

}
