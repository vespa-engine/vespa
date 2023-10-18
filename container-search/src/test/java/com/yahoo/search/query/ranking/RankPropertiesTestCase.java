// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.Query;
import com.yahoo.search.query.Ranking;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author arnej
 */
public class RankPropertiesTestCase {

    @Test
    void requireThatGetAsTensorCanGetDoublesAndTensors() {
        TensorType ttype = new TensorType.Builder().mapped("cat").build();
        Tensor mappedTensor = Tensor.from(ttype, "{ {cat:foo}:2.5, {cat:bar}:1.25 }");
        RankFeatures f = new RankFeatures(new Ranking(new Query()));
        f.put("query(myDouble)", 42.75);
        f.put("query(myTensor)", mappedTensor);
        RankProperties p = new RankProperties();
        f.prepare(p);
        var optT = p.getAsTensor("myDouble");
        assertEquals(true, optT.isPresent());
        assertEquals(TensorType.empty, optT.get().type());
        assertEquals(42.75, optT.get().asDouble());
        optT = p.getAsTensor("myTensor");
        assertEquals(true, optT.isPresent());
        assertEquals(mappedTensor, optT.get());
    }

    @Test
    void requireThatGetAsTensorFailsOnStrings() {
        RankFeatures f = new RankFeatures(new Ranking(new Query()));
        // common mistake:
        f.put("query(myTensor)", "{ {cat:foo}:2.5, {cat:bar}:1.25 }");
        RankProperties p = new RankProperties();
        f.prepare(p);
        var ex = assertThrows(IllegalArgumentException.class, () -> p.getAsTensor("myTensor"));
        assertEquals("Expected 'myTensor' to be a tensor or double, " +
                     "but it is '{ {cat:foo}:2.5, {cat:bar}:1.25 }', " +
                     "this usually means that 'myTensor' is not defined in the schema. " +
                     "See https://docs.vespa.ai/en/tensor-user-guide.html#querying-with-tensors", ex.getMessage());
    }
}
