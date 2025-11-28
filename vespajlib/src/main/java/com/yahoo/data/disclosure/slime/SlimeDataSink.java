// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.disclosure.slime;

import com.yahoo.data.disclosure.DataSink;
import com.yahoo.slime.ArrayInserter;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inserter;
import com.yahoo.slime.ObjectInserter;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeInserter;
import com.yahoo.slime.Type;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Slime builder that objects implementing {@link com.yahoo.data.disclosure.DataSource} can emit into.
 * <p>
 * This class is intended for testing. Should not be used in production.
 *
 * @author johsol
 */
public class SlimeDataSink implements DataSink {

    private Slime slime = new Slime();
    private Deque<Cursor> stack = new ArrayDeque<>();
    private String key;

    public Slime getSlime() {
        return slime;
    }

    private Cursor top() {
        return stack.peek();
    }

    private Inserter makeInserter() {
        if (stack.isEmpty()) {
            return new SlimeInserter(slime);
        } else if (top().type() == Type.OBJECT) {
            return new ObjectInserter(top(), key);
        } else {
            return new ArrayInserter(top());
        }
    }

    @Override
    public void fieldName(String utf16, byte[] utf8) {
        if (utf16 != null) {
            key = utf16;
        } else {
            // since this class is intended for testing we convert the string to utf16 in this case
            key = new String(utf8, StandardCharsets.UTF_8);
        }
    }

    @Override
    public void startObject() {
        stack.push(makeInserter().insertOBJECT());
    }

    @Override
    public void endObject() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    @Override
    public void startArray() {
        stack.push(makeInserter().insertARRAY());
    }

    @Override
    public void endArray() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    @Override
    public void emptyValue() {
        makeInserter().insertNIX();
    }

    @Override
    public void booleanValue(boolean v) {
        makeInserter().insertBOOL(v);
    }

    @Override
    public void longValue(long v) {
        makeInserter().insertLONG(v);
    }

    @Override
    public void doubleValue(double v) {
        makeInserter().insertDOUBLE(v);
    }

    @Override
    public void stringValue(String utf16, byte[] utf8) {
        String v;
        if (utf16 != null) {
            v = utf16;
        } else {
            // since this class is intended for testing we convert the string to utf16 in this case
            v = new String(utf8, StandardCharsets.UTF_8);
        }

        makeInserter().insertSTRING(v);
    }

    @Override
    public void dataValue(byte[] data) {
        makeInserter().insertDATA(data);
    }
}
