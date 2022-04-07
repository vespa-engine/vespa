// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.search.Result;
import com.yahoo.search.grouping.GroupingRequest;

/**
 * A labeled group list in the grouping result model. It is contained in {@link Group}, and
 * contains one or more {@link Group groups} itself, allowing for a hierarchy of grouping results. Use the {@link
 * GroupingRequest#getResultGroup(Result)} to retrieve grouping results.
 *
 * @author Simon Thoresen Hult
 */
public class GroupList extends AbstractList {

    /**
     * Constructs a new instance of this class.
     *
     * @param label the label to assign to this
     */
    public GroupList(String label) {
        super("grouplist", label);
    }

}
