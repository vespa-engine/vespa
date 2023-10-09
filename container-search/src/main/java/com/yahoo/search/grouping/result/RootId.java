// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

/**
 * This class is used in {@link RootGroup} instances.
 *
 * @author Simon Thoresen Hult
 */
public class RootId extends GroupId {

    public RootId(int id) {
        super("root", id);
    }

}
