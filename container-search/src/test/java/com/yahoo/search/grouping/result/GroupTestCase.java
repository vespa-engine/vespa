// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.result;

import com.yahoo.search.result.Hit;
import com.yahoo.search.result.Relevance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class GroupTestCase {

    @Test
    void requireThatListsAreAccessibleByLabel() {
        Group grp = new Group(new LongId(69L), new Relevance(1));
        grp.add(new Hit("hit"));
        grp.add(new HitList("hitList"));
        grp.add(new GroupList("groupList"));

        assertNotNull(grp.getGroupList("groupList"));
        assertNull(grp.getGroupList("unknownGroupList"));
        assertNull(grp.getGroupList("hitList"));

        assertNotNull(grp.getHitList("hitList"));
        assertNull(grp.getHitList("unknownHitList"));
        assertNull(grp.getHitList("groupList"));
    }
}
