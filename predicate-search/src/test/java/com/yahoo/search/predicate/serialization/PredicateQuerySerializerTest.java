// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.serialization;

import com.yahoo.search.predicate.PredicateQuery;
import com.yahoo.search.predicate.SubqueryBitmap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class PredicateQuerySerializerTest {

    @Test
    void require_that_query_is_correctly_parsed_and_written_back_to_json() throws Exception {
        String json =
                "{\"features\":[" +
                        "{\"k\":\"k1\",\"v\":\"value1\",\"s\":\"0x1\"}," +
                        "{\"k\":\"k2\",\"v\":\"value2\",\"s\":\"0x3\"}" +
                        "],\"rangeFeatures\":[" +
                        "{\"k\":\"range1\",\"v\":123456789123,\"s\":\"0xffff\"}," +
                        "{\"k\":\"range2\",\"v\":0}" +
                        "]}";
        PredicateQuerySerializer serializer = new PredicateQuerySerializer();
        PredicateQuery query = serializer.fromJSON(json);
        List<PredicateQuery.Feature> features = query.getFeatures();
        PredicateQuery.Feature f1 = features.get(0);
        PredicateQuery.Feature f2 = features.get(1);
        List<PredicateQuery.RangeFeature> rangeFeatures = query.getRangeFeatures();
        PredicateQuery.RangeFeature r1 = rangeFeatures.get(0);
        PredicateQuery.RangeFeature r2 = rangeFeatures.get(1);

        assertEquals("k1", f1.key);
        assertEquals("value1", f1.value);
        assertEquals(0x1, f1.subqueryBitmap);

        assertEquals("k2", f2.key);
        assertEquals("value2", f2.value);
        assertEquals(0x3, f2.subqueryBitmap);

        assertEquals("range1", r1.key);
        assertEquals(123456789123l, r1.value);
        assertEquals(0xFFFF, r1.subqueryBitmap);

        assertEquals("range2", r2.key);
        assertEquals(0l, r2.value);
        assertEquals(SubqueryBitmap.DEFAULT_VALUE, r2.subqueryBitmap);

        assertEquals(json, serializer.toJSON(query));
    }
}
