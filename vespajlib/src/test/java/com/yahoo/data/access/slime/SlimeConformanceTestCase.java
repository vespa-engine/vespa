// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access.slime;


public class SlimeConformanceTestCase extends com.yahoo.data.access.InspectorConformanceTestBase {

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
        com.yahoo.slime.Slime slime = new com.yahoo.slime.Slime();
        {
            com.yahoo.slime.Cursor arr = slime.setArray();
            arr.addNix();
            arr.addBool(true);
            arr.addLong(10);
            arr.addDouble(5.75);
            {
                com.yahoo.slime.Cursor obj = arr.addObject();
                obj.setString("foo", "foo_value");
                obj.setData("bar", new byte[] { (byte)4, (byte)2 });
                {
                    com.yahoo.slime.Cursor nested_array = obj.setArray("nested");
                    {
                        com.yahoo.slime.Cursor nested_object = nested_array.addObject();
                        nested_object.setString("hidden", "treasure");
                    }
                }
            }
        }
        return new SlimeAdapter(slime.get());
    }
}
