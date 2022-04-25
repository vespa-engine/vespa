// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.config;

import com.yahoo.tensor.TensorType;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class SchemaInfoTest {

    @Test
    public void testSchemaInfoConfiguration() {
        assertEquals(SchemaInfoTester.createSchemaInfoFromConfig(), SchemaInfoTester.createSchemaInfo());
    }

    @Test
    public void testInputResolution() {
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
