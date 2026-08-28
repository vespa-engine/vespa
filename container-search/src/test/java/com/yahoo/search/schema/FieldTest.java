// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author johsol
 */
public class FieldTest {

    @Test
    public void requireMapIsParsed() {
        // Primitive key and value
        assertMapType("map<string,int>", Field.Type.Kind.STRING, Field.Type.Kind.INT);
        assertMapType("map<int,string>", Field.Type.Kind.INT, Field.Type.Kind.STRING);
        assertMapType("map<long,double>", Field.Type.Kind.LONG, Field.Type.Kind.DOUBLE);
        assertMapType("map<string,bool>", Field.Type.Kind.STRING, Field.Type.Kind.BOOL);

        // Whitespace
        assertMapType("map<string, int>", Field.Type.Kind.STRING, Field.Type.Kind.INT);
        assertMapType("map< string , int >", Field.Type.Kind.STRING, Field.Type.Kind.INT);

        // Struct value: the spec is the bare struct name
        assertMapType("map<int,mystruct>", Field.Type.Kind.INT, Field.Type.Kind.STRUCT);

        // Collection values
        assertMapType("map<string,array<int>>", Field.Type.Kind.STRING, Field.Type.Kind.ARRAY);
        assertMapType("map<string,weightedset<string>>", Field.Type.Kind.STRING, Field.Type.Kind.WEIGHTEDSET);

        // Nested map value: the inner map is parsed as well. Keys are always primitive, see DisallowComplexMapAndWsetKeyTypes.
        var nested = assertMapType("map<int,map<string,int>>", Field.Type.Kind.INT, Field.Type.Kind.MAP);
        var innerMap = assertInstanceOf(Field.MapFieldType.class, nested.valueType());
        assertEquals(Field.Type.Kind.STRING, innerMap.keyType().kind());
        assertEquals(Field.Type.Kind.INT, innerMap.valueType().kind());

        // Tensor value
        var withTensor = assertMapType("map<string,tensor(x[2],y[2])>", Field.Type.Kind.STRING, Field.Type.Kind.TENSOR);
        var tensorType = assertInstanceOf(Field.TensorFieldType.class, withTensor.valueType());
        assertEquals(TensorType.fromSpec("tensor(x[2],y[2])"), tensorType.tensorType());
        var withMappedTensor = assertMapType("map<string,tensor<float>(a{},b{})>", Field.Type.Kind.STRING, Field.Type.Kind.TENSOR);
        var mappedTensorType = assertInstanceOf(Field.TensorFieldType.class, withMappedTensor.valueType());
        assertEquals(TensorType.fromSpec("tensor<float>(a{},b{})"), mappedTensorType.tensorType());
    }

    /** Returns the map type for further assertions. */
    Field.MapFieldType assertMapType(String typeSpec, Field.Type.Kind key, Field.Type.Kind value) {
        var type = Field.Type.from(typeSpec);
        var mapType = assertInstanceOf(Field.MapFieldType.class, type);
        assertEquals(key, mapType.keyType().kind());
        assertEquals(value, mapType.valueType().kind());
        return mapType;
    }

}
