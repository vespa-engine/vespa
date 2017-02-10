package com.yahoo.document.json.readers;


import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;

public class JsonParserHelpers {
    public static void expectArrayStart(JsonToken token) {
        Preconditions.checkState(token == JsonToken.START_ARRAY, "Expected start of array, got %s", token);
    }

    public static void expectArrayEnd(JsonToken token) {
        Preconditions.checkState(token == JsonToken.END_ARRAY, "Expected start of array, got %s", token);
    }

    public static void expectObjectStart(JsonToken token) {
        Preconditions.checkState(token == JsonToken.START_OBJECT, "Expected start of JSON object, got %s", token);
    }

    public static void expectObjectEnd(JsonToken token) {
        Preconditions.checkState(token == JsonToken.END_OBJECT, "Expected end of JSON object, got %s", token);
    }

    public static void expectCompositeEnd(JsonToken token) {
        Preconditions.checkState(token.isStructEnd(), "Expected end of composite, got %s", token);
    }
}
