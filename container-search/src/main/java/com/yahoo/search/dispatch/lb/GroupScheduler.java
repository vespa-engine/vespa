// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import java.util.Optional;
import java.util.Set;

/**
 * A strategy for selecting a content group for a request.
 *
 * @author Olli Virtanen
 */
public interface GroupScheduler {

    /**
     * Returns the selected group for a request, or empty if no non-rejected group is available.
     *
     * @param rejectedGroups a set of ids of groups this is not allowed to select
     */
    Optional<TrackedGroup> takeNextGroup(Set<Integer> rejectedGroups);

}
