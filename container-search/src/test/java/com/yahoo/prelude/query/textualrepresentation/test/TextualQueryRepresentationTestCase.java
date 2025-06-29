// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.textualrepresentation.test;

import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.textualrepresentation.Discloser;
import com.yahoo.prelude.query.textualrepresentation.TextualQueryRepresentation;
import com.yahoo.text.Utf8;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test of TextualQueryRepresentation.
 *
 * @author Tony Vaagenes
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TextualQueryRepresentationTestCase {

    private enum ExampleEnum {
        example;
    }

    private static class MockItem extends Item {
        private final String name;

        @Override
        public void setIndexName(String index) {
        }

        @Override
        public ItemType getItemType() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int encode(ByteBuffer buffer) {
            return 0;
        }

        @Override
        public int getTermCount() {
            return 0;
        }

        @Override
        protected void appendBodyString(StringBuilder buffer) {
        }

        MockItem(String name) {
            this.name = name;
        }
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

    @Test
    void testBasic() throws Exception {
        String basicText = getTextualQueryRepresentation(basic);
        assertEquals(getCorrect("basic.txt"), basicText);

    }

    @Test
    void testComposite() throws Exception {
        String compositeText = getTextualQueryRepresentation(composite);
        assertEquals(getCorrect("composite.txt"), compositeText);
    }

    private String getCorrect(String filename) throws Exception {
        BufferedReader reader = new BufferedReader(Utf8.createReader(
                "src/test/java/com/yahoo/prelude/query/textualrepresentation/test/" + filename));
        StringBuilder result = new StringBuilder();
        for (String line; (line = reader.readLine()) != null;)
            result.append(line).append('\n');
        return result.toString();
    }

}
