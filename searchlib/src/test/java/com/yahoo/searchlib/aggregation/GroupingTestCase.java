// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation;

import com.yahoo.searchlib.expression.NullResultNode;
import com.yahoo.searchlib.expression.StringBucketResultNode;
import com.yahoo.vespa.objects.BufferSerializer;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingTestCase {

    private static final int VALID_BYTE_INDEX = 8;

    @Test
    public void requireThatIdAccessorsWork() {
        Grouping grouping = new Grouping();
        assertEquals(0, grouping.getId());

        grouping = new Grouping(6);
        assertEquals(6, grouping.getId());
        grouping.setId(9);
        assertEquals(9, grouping.getId());

        Grouping other = new Grouping(6);
        assertFalse(grouping.equals(other));
        other.setId(9);
        assertEquals(grouping, other);

        assertEquals(grouping, grouping.clone());
        assertSerialize(grouping);
    }

    @Test
    public void requireThatAllAccessorsWork() {
        Grouping grouping = new Grouping();
        assertFalse(grouping.getAll());
        grouping.setAll(true);
        assertTrue(grouping.getAll());

        Grouping other = new Grouping();
        assertFalse(grouping.equals(other));
        other.setAll(true);
        assertEquals(grouping, other);

        assertEquals(grouping, grouping.clone());
        assertSerialize(grouping);
    }

    @Test
    public void requireThatTopNAccessorsWork() {
        Grouping grouping = new Grouping();
        assertEquals(-1, grouping.getTopN());
        grouping.setTopN(69);
        assertEquals(69, grouping.getTopN());

        Grouping other = new Grouping();
        assertFalse(grouping.equals(other));
        other.setTopN(69);
        assertEquals(grouping, other);

        assertEquals(grouping, grouping.clone());
        assertSerialize(grouping);
    }

    @Test
    public void requireThatFirstLevelAccessorsWork() {
        Grouping grouping = new Grouping();
        assertEquals(0, grouping.getFirstLevel());
        grouping.setFirstLevel(69);
        assertEquals(69, grouping.getFirstLevel());

        Grouping other = new Grouping();
        assertFalse(grouping.equals(other));
        other.setFirstLevel(69);
        assertEquals(grouping, other);

        assertEquals(grouping, grouping.clone());
        assertSerialize(grouping);
    }

    @Test
    public void requireThatLastLevelAccessorsWork() {
        Grouping grouping = new Grouping();
        assertEquals(0, grouping.getLastLevel());
        grouping.setLastLevel(69);
        assertEquals(69, grouping.getLastLevel());

        Grouping other = new Grouping();
        assertFalse(grouping.equals(other));
        other.setLastLevel(69);
        assertEquals(grouping, other);

        assertEquals(grouping, grouping.clone());
        assertSerialize(grouping);
    }

    @Test
    public void requireThatRootAccessorsWork() {
        Grouping grouping = new Grouping();
        assertEquals(new Group(), grouping.getRoot());
        try {
            grouping.setRoot(null);
            fail();
        } catch (NullPointerException e) {

        }
        Group root = new Group().setRank(6.9);
        grouping.setRoot(root);
        assertEquals(root, grouping.getRoot());

        Grouping other = new Grouping();
        assertFalse(grouping.equals(other));
        other.setRoot(root);
        assertEquals(grouping, other);

        assertEquals(grouping, grouping.clone());
        assertSerialize(grouping);
    }

    @Test
    public void requireThatLevelAccessorsWork() {
        Grouping grouping = new Grouping();
        assertEquals(Collections.emptyList(), grouping.getLevels());
        try {
            grouping.addLevel(null);
            fail();
        } catch (NullPointerException e) {

        }
        GroupingLevel level = new GroupingLevel();
        grouping.addLevel(level);
        assertEquals(Arrays.asList(level), grouping.getLevels());

        Grouping other = new Grouping();
        assertFalse(grouping.equals(other));
        other.addLevel(level);
        assertEquals(grouping, other);

        assertEquals(grouping, grouping.clone());
        assertSerialize(grouping);
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(new Grouping().hashCode(), new Grouping().hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        assertFalse(new Grouping().equals(new Object()));
        assertTrue(new Grouping().equals(new Grouping()));
    }

    @Test
    public void requireThatValidAccessorsWork() {
        byte[] arr = new byte[1024];
        BufferSerializer buf = new BufferSerializer(arr);
        Grouping grouping = new Grouping();
        grouping.serializeWithId(buf);
        buf.flip();
        assertEquals(1, arr[VALID_BYTE_INDEX]);
        arr[VALID_BYTE_INDEX] = 0;

        Grouping other = (Grouping)Grouping.create(buf);
        assertFalse(other.valid());

        assertEquals(grouping, grouping.clone());
        assertSerialize(grouping);
    }

    @Test
    public void requireThatSetForceSinglePassWorks() {
        assertFalse(new Grouping().getForceSinglePass());
        assertFalse(new Grouping().setForceSinglePass(false).getForceSinglePass());
        assertTrue(new Grouping().setForceSinglePass(true).getForceSinglePass());
    }

    @Test
    public void requireThatNeedDeepResultCollectionWorks() {
        assertFalse(new Grouping().addLevel(new GroupingLevel().setGroupPrototype(new Group())).needDeepResultCollection());
        assertTrue(new Grouping().addLevel(new GroupingLevel().setGroupPrototype(new Group().addOrderBy(new CountAggregationResult(9), true))).needDeepResultCollection());
    }

    @Test
    public void requireThatUseSinglePassWorks() {
        assertFalse(new Grouping().useSinglePass());
        assertFalse(new Grouping().setForceSinglePass(false).useSinglePass());
        assertTrue(new Grouping().setForceSinglePass(true).useSinglePass());
        assertFalse(new Grouping().addLevel(new GroupingLevel().setGroupPrototype(new Group())).useSinglePass());
        assertTrue(new Grouping().addLevel(new GroupingLevel().setGroupPrototype(new Group().addOrderBy(new CountAggregationResult(9), true))).useSinglePass());
    }

    @Test
    public void requireThatUnifyNullReplacesEmptyBucketIds() {
        Grouping grouping = new Grouping();
        grouping.getRoot().addChild(new Group().setId(new StringBucketResultNode()));
        grouping.setLastLevel(1); // otherwise unifyNull will not operate on it
        grouping.unifyNull();
        assertEquals(NullResultNode.class, grouping.getRoot().getChildren().get(0).getId().getClass());
    }

    @Test
    public void requireThatUnifyNullDoesNotReplaceNonEmptyBucketIds() {
        Grouping grouping = new Grouping();
        grouping.getRoot().addChild(new Group().setId(new StringBucketResultNode("6", "9")));
        grouping.setLastLevel(1); // otherwise unifyNull will not operate on it
        grouping.unifyNull();
        assertEquals(StringBucketResultNode.class, grouping.getRoot().getChildren().get(0).getId().getClass());
    }

    private static void assertSerialize(Grouping grouping) {
        BufferSerializer buf = new BufferSerializer();
        grouping.serializeWithId(buf);

        buf.flip();
        Grouping other = (Grouping)Grouping.create(buf);
        assertEquals(grouping, other);
    }
}

