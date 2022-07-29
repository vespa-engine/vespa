// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class SchemaInfoTest {

    @Test
    void testSchemaInfoConfiguration() {
        assertEquals(SchemaInfoTester.createSchemaInfoFromConfig(), SchemaInfoTester.createSchemaInfo());
    }

    @Test
    void testInputResolution() {
        var tester = new SchemaInfoTester();
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "", "", "commonProfile", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "ab", "", "commonProfile", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "a", "", "commonProfile", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "b", "", "commonProfile", "query(myTensor1)");

        tester.assertInputConflict(TensorType.fromSpec("tensor(a{},b{})"),
                "", "", "inconsistent", "query(myTensor1)");
        tester.assertInputConflict(TensorType.fromSpec("tensor(a{},b{})"),
                "ab", "", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "ab", "a", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(x[10])"),
                "ab", "b", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "a", "", "inconsistent", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(x[10])"),
                "b", "", "inconsistent", "query(myTensor1)");
        tester.assertInput(null,
                "a", "", "bOnly", "query(myTensor1)");
        tester.assertInput(TensorType.fromSpec("tensor(a{},b{})"),
                "ab", "", "bOnly", "query(myTensor1)");
    }

}
