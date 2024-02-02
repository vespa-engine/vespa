package com.yahoo.document.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.json.TokenBuffer.Token;
import org.junit.Test;

import java.io.IOException;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author jonmv
 */
public class LazyTokenBufferTest {

    @Test
    public void testBuffer() throws IOException {
        String json = """
                      {
                        "fields": {
                          "foo": "bar",
                          "baz": [1, 2, 3],
                          "quu": { "qux": null }
                        }
                      }""";
        JsonParser parser = new JsonFactory().createParser(json);
        parser.nextValue();
        parser.nextValue();
        assertEquals(JsonToken.START_OBJECT, parser.currentToken());
        assertEquals("fields", parser.currentName());

        // Peeking through the buffer doesn't change nesting.
        LazyTokenBuffer buffer = new LazyTokenBuffer(parser);
        assertEquals(JsonToken.START_OBJECT, buffer.current());
        assertEquals("fields", buffer.currentName());
        assertEquals(1, buffer.nesting());

        Supplier<Token> lookahead = buffer.lookahead();
        Token peek = lookahead.get();
        assertEquals(JsonToken.VALUE_STRING, peek.token);
        assertEquals("foo", peek.name);
        assertEquals("bar", peek.text);
        assertEquals(1, buffer.nesting());

        peek = lookahead.get();
        assertEquals(JsonToken.START_ARRAY, peek.token);
        assertEquals("baz", peek.name);
        assertEquals(1, buffer.nesting());

        peek = lookahead.get();
        assertEquals(JsonToken.VALUE_NUMBER_INT, peek.token);
        assertEquals("1", peek.text);

        peek = lookahead.get();
        assertEquals(JsonToken.VALUE_NUMBER_INT, peek.token);
        assertEquals("2", peek.text);

        peek = lookahead.get();
        assertEquals(JsonToken.VALUE_NUMBER_INT, peek.token);
        assertEquals("3", peek.text);

        peek = lookahead.get();
        assertEquals(JsonToken.END_ARRAY, peek.token);
        assertEquals(1, buffer.nesting());

        peek = lookahead.get();
        assertEquals(JsonToken.START_OBJECT, peek.token);
        assertEquals("quu", peek.name);
        assertEquals(1, buffer.nesting());

        peek = lookahead.get();
        assertEquals(JsonToken.VALUE_NULL, peek.token);
        assertEquals("qux", peek.name);

        peek = lookahead.get();
        assertEquals(JsonToken.END_OBJECT, peek.token);
        assertEquals(1, buffer.nesting());

        peek = lookahead.get();
        assertEquals(JsonToken.END_OBJECT, peek.token);
        assertEquals(1, buffer.nesting());

        peek = lookahead.get();
        assertNull(peek);

        // Parser is now at the end.
        assertEquals(JsonToken.END_OBJECT, parser.nextToken());
        assertNull(parser.nextToken());

        // Repeat iterating through the buffer, this time advancing it, and see that nesting changes.
        assertEquals(JsonToken.VALUE_STRING, buffer.next());
        assertEquals("foo", buffer.currentName());
        assertEquals("bar", buffer.currentText());
        assertEquals(1, buffer.nesting());

        assertEquals(JsonToken.START_ARRAY, buffer.next());
        assertEquals("baz", buffer.currentName());
        assertEquals(2, buffer.nesting());

        assertEquals(JsonToken.VALUE_NUMBER_INT, buffer.next());
        assertEquals("1", buffer.currentText());

        assertEquals(JsonToken.VALUE_NUMBER_INT, buffer.next());
        assertEquals("2", buffer.currentText());

        assertEquals(JsonToken.VALUE_NUMBER_INT, buffer.next());
        assertEquals("3", buffer.currentText());

        assertEquals(JsonToken.END_ARRAY, buffer.next());
        assertEquals(1, buffer.nesting());

        assertEquals(JsonToken.START_OBJECT, buffer.next());
        assertEquals("quu", buffer.currentName());
        assertEquals(2, buffer.nesting());

        assertEquals(JsonToken.VALUE_NULL, buffer.next());
        assertEquals("qux", buffer.currentName());

        assertEquals(JsonToken.END_OBJECT, buffer.next());
        assertEquals(1, buffer.nesting());

        assertEquals(JsonToken.END_OBJECT, buffer.next());
        assertEquals(0, buffer.nesting());

        assertNull(buffer.next());
    }

}
