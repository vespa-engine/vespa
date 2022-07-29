// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.match.test;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.search.Result;
import com.yahoo.search.match.DocumentDb;
import com.yahoo.text.MapParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class DocumentDbTest {

    @Test
    @Disabled
    void testWand() {
        DocumentDb db = new DocumentDb();
        db.put(createFeatureDocument("1", "[a:7, b:5, c:3]"));
        db.put(createFeatureDocument("2", "[a:2, b:1, c:4]"));
        //Result r = execute(createWandQuery("[a:1, b:3, c:5]"));
        //assertEquals(67,r.hits().get(0).getRelevance());
        //assertEquals(25, r.hits().get(1).getRelevance());
    }

    private DocumentPut createFeatureDocument(String localId, String features) {
        DocumentType type = new DocumentType("withFeature");
        type.addField("features", new WeightedSetDataType(DataType.STRING,true,true));
        Document d = new Document(type, new DocumentId("id:test::" + localId));
        d.setFieldValue("features",fillFromString(new WeightedSet(DataType.STRING), features));
        return new DocumentPut(d);
    }

    // TODO: Move to weightedset
    // TODO: Don't pass through a map
    private WeightedSet fillFromString(WeightedSet s, String values) {
        //new IntMapParser().parse();
        return null;
    }

    private static class IntMapParser extends MapParser<Integer> {

        @Override
        protected Integer parseValue(String s) {
            return Integer.parseInt(s);
        }

    }


}
