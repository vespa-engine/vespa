// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParsedType;
import com.yahoo.tensor.TensorType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author johsol
 */
public class ParsedTypeTest {

    private static class NiceNameFixture extends HashMap<String, ParsedType> {
        public NiceNameFixture addIdentity(String name) {
            put(name, make(name));
            return this;
        }

        public NiceNameFixture add(String expected, ParsedType type) {
            put(expected, type);
            return this;
        }
    }

    @Test
    public void requireParsedTypeHasNiceNames() {
        var fixture = new NiceNameFixture();
        fixture.addIdentity("byte")
                .addIdentity("int")
                .addIdentity("long")
                .addIdentity("float")
                .addIdentity("float16")
                .addIdentity("double")
                .addIdentity("bool")
                .addIdentity("string")
                .addIdentity("raw")
                .addIdentity("tag")
                .addIdentity("position")
                .addIdentity("predicate")
                .addIdentity("uri");

        var stringType = make("string");
        var intType = make("int");
        var structType = ParsedType.structType("Person");
        fixture.add("array<string>", ParsedType.arrayOf(stringType))
                .add("map<string, int>", ParsedType.mapType(stringType, intType))
                .add("weightedset<int>", ParsedType.wsetOf(intType))
                .add("reference<other_doc>", makeReference("other_doc"))
                .add("tensor<int8>(key{},x[2])", makeTensorType("tensor<int8>(key{}, x[2])"))
                .add("struct Person", structType)
                .add("document music", ParsedType.documentType("music"))
                .add("array<Person>", ParsedType.arrayOf(structType))
                .add("annotationreference<mytype>", ParsedType.annotationRef("mytype"))
                .add("map<int, map<string, Person>>", ParsedType.mapType(intType, ParsedType.mapType(stringType, structType)));

        for (var entry : fixture.entrySet()) {
            var name = entry.getKey();
            var type = entry.getValue().toNiceName();
            assertEquals(name, type);
        }
    }

    private static ParsedType make(String name) {
        return ParsedType.fromName(name);
    }

    private static ParsedType makeReference(String name) {
        return ParsedType.documentRef(ParsedType.documentType(name));
    }

    private static ParsedType makeTensorType(String spec) {
        return ParsedType.tensorType(TensorType.fromSpec(spec));
    }

}
