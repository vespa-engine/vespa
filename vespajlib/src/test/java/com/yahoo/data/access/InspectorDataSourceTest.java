// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access;

import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.data.disclosure.slime.SlimeDataSink;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author johsol
 */
public class InspectorDataSourceTest {

    private void assertSlime(Slime expected, Slime actual) {
        assertTrue(expected.get().equalTo(actual.get()),
                () -> "Expected " + SlimeUtils.toJson(expected) +
                        " but got " + SlimeUtils.toJson(actual));
    }

    @Test
    public void testValuesInObjectIsPreserved() {
        var expected = SlimeUtils.jsonToSlime("{ int: 1024, " +
                "  bool: true," +
                "  double: 3.5," +
                "  my_null: null," +
                "  string: 'hello' }");

        var inspector = new SlimeAdapter(expected.get());
        var dataSource = new InspectorDataSource(inspector);
        var actual = SlimeDataSink.buildSlime(dataSource);
        assertSlime(expected, actual);
    }

    @Test
    public void testValuesInArrayIsPreserved() {
        var expected = SlimeUtils.jsonToSlime("[1, true, 2.5, 'foo', null]");

        var inspector = new SlimeAdapter(expected.get());
        var dataSource = new InspectorDataSource(inspector);
        var actual = SlimeDataSink.buildSlime(dataSource);
        assertSlime(expected, actual);
    }

    @Test
    public void testNestedObjectAndArrayArePreserved() {
        var expected = SlimeUtils.jsonToSlime("{ nums: [1, 2], meta: { ok: true } }");

        var inspector = new SlimeAdapter(expected.get());
        var dataSource = new InspectorDataSource(inspector);
        var actual = SlimeDataSink.buildSlime(dataSource);
        assertSlime(expected, actual);
    }

    @Test
    public void testArrayOfObjectsArePreserved() {
        var expected = SlimeUtils.jsonToSlime("[ { id: 1 }, { id: 2 } ]");

        var inspector = new SlimeAdapter(expected.get());
        var dataSource = new InspectorDataSource(inspector);
        var actual = SlimeDataSink.buildSlime(dataSource);
        assertSlime(expected, actual);
    }

}
