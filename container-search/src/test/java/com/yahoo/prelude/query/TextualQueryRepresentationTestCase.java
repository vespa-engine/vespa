// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import com.yahoo.prelude.query.textualrepresentation.TextualQueryRepresentation;
import org.junit.jupiter.api.Test;

/**
 * Test of TextualQueryRepresentation.
 *
 * @author Tony Vaagenes
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TextualQueryRepresentationTestCase {

    @Test
    void testBasic() {
        String expected = """
                basic[01=null 02="a string." 03=1234 04=true 05=example 06=(1 2 3) 07=("x" "y" "z") 08=() 09=(1 2 3) 10=map(1=>"one" 2=>"two" 3=>("x" "y" "z"))]{"example-value: \\"12\\""}""";
        assertEquals(expected, getTextualQueryRepresentation(basic));
    }

    @Test
    void testComposite() {
        String expected = """
                composite[reference=0]{
                  basic[%id=0 01=null 02="a string." 03=1234 04=true 05=example 06=(1 2 3) 07=("x" "y" "z") 08=() 09=(1 2 3) 10=map(1=>"one" 2=>"two" 3=>("x" "y" "z"))]{"example-value: \\"12\\""}
                  basic[01=null 02="a string." 03=1234 04=true 05=example 06=(1 2 3) 07=("x" "y" "z") 08=() 09=(1 2 3) 10=map(1=>"one" 2=>"two" 3=>("x" "y" "z"))]{"example-value: \\"12\\""}
                }
                """;
        assertEquals(expected, getTextualQueryRepresentation(composite));
    }

    private enum ExampleEnum {
        example;
    }

    private final Item basic = new MockItem("basic") {
        @Override
        public void disclose(Discloser discloser) {
            Map<Integer, Object> exampleMap = new HashMap<>();
            exampleMap.put(1, "one");
            exampleMap.put(2, "two");
            exampleMap.put(3, List.of('x', 'y', 'z'));

            discloser.addProperty("01", null);
            discloser.addProperty("02", "a string.");
            discloser.addProperty("03", 1234);
            discloser.addProperty("04", true);
            discloser.addProperty("05", ExampleEnum.example);
            discloser.addProperty("06", new int[]{1, 2, 3});
            discloser.addProperty("07", List.of('x', 'y', 'z'));
            discloser.addProperty("08", new ArrayList());
            discloser.addProperty("09", new HashSet(List.of(1, 2, 3)));
            discloser.addProperty("10", exampleMap);

            discloser.setValue("example-value: \"12\"");
        }
    };

    private final Item composite = new MockItem("composite") {
        @Override
        public void disclose(Discloser discloser) {
            discloser.addProperty("reference", basic);
            discloser.addChild(basic);
            discloser.addChild(basic.clone());
        }
    };

    private String getTextualQueryRepresentation(Item item) {
        return new TextualQueryRepresentation(item).toString();
    }

}
