// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access.simple;


import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


public class SimpleConformanceTestCase extends com.yahoo.data.access.InspectorConformanceTestBase {

    // ARRAY {
    //   [0]: EMPTY
    //   [1]: BOOL: true
    //   [2]: LONG: 10
    //   [3]: DOUBLE: 5.75
    //   [4]: OBJECT {
    //     "foo": STRING: "foo_value"
    //     "bar": DATA: 0x04 0x02
    //     "nested": ARRAY {
    //       [0]: OBJECT {
    //         "hidden": STRING: "treasure"
    //       }
    //     }
    //   }
    // }
    public com.yahoo.data.access.Inspector getData() {
        return new Value.ArrayValue()
            .add(new Value.EmptyValue())
            .add(new Value.BoolValue(true))
            .add(new Value.LongValue(10L))
            .add(new Value.DoubleValue(5.75))
            .add(new Value.ObjectValue()
                 .put("foo", new Value.StringValue("foo_value"))
                 .put("bar", new Value.DataValue(new byte[] { (byte)4, (byte)2 }))
                 .put("nested", new Value.ArrayValue()
                      .add(new Value.ObjectValue()
                           .put("hidden", new Value.StringValue("treasure")))));
    }

    @Test
    public void testSingletons() {
        assertThat(Value.empty().valid(), is(true));
        assertThat(Value.empty().type(), is(com.yahoo.data.access.Type.EMPTY));
        assertThat(Value.invalid().valid(), is(false));
        assertThat(Value.invalid().type(), is(com.yahoo.data.access.Type.EMPTY));
    }

    @Test
    public void testToString() {
        String json = getData().toString();
        String correct = "[null,true,10,5.75,{\"foo\":\"foo_value\",\"bar\":\"0x0402\",\"nested\":[{\"hidden\":\"treasure\"}]}]";
        assertThat(json, is(correct));
    }
}
