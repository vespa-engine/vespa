// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author geirst
 */
public class TensorFieldTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void requireThatTensorFieldCannotBeOfCollectionType() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'test', field 'f1': A field with collection type of tensor is not supported. Use simple type 'tensor' instead.");
        SearchBuilder.createFromString(getSd("field f1 type array<tensor(x{})> {}"));
    }

    @Test
    public void requireThatTensorFieldCannotBeIndexField() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'test', field 'f1': A field of type 'tensor' cannot be specified as an 'index' field.");
        SearchBuilder.createFromString(getSd("field f1 type tensor(x{}) { indexing: index }"));
    }

    @Test
    public void requireThatTensorAttributeCannotBeFastSearch() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("For search 'test', field 'f1': An attribute of type 'tensor' cannot be 'fast-search'.");
        SearchBuilder.createFromString(getSd("field f1 type tensor(x{}) { indexing: attribute \n attribute: fast-search }"));
    }

    @Test
    public void requireThatIllegalTensorTypeSpecThrowsException() throws ParseException {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Field type: Illegal tensor type spec: Failed parsing element 'invalid' in type spec 'tensor(invalid)'");
        SearchBuilder.createFromString(getSd("field f1 type tensor(invalid) { indexing: attribute }"));
    }

    private static String getSd(String field) {
        return "search test {\n document test {\n" + field + "}\n}\n";
    }

}
