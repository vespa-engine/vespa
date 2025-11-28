// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.disclosure.slime;

import com.yahoo.data.disclosure.DataSink;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
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
    private Cursor root;
    private Deque<Cursor> stack = new ArrayDeque<>();
    private String key;

    public Slime getSlime() {
        return slime;
    }

    private boolean topIsObject() {
        return !stack.isEmpty() && stack.peek().type() == Type.OBJECT;
    }

    private Cursor top() {
        return stack.peek();
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
        if (root == null) {
            root = slime.setObject();
            stack.push(root);
        } else {
            if (topIsObject()) {
                var obj = top().setObject(key);
                stack.push(obj);
            } else {
                var obj = top().addObject();
                stack.push(obj);
            }
        }
    }

    @Override
    public void endObject() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    @Override
    public void startArray() {
        if (root == null) {
            root = slime.setArray();
            stack.push(root);
        } else {
            if (topIsObject()) {
                var obj = top().setArray(key);
                stack.push(obj);
            } else {
                var arr = top().addArray();
                stack.push(arr);
            }
        }
    }

    @Override
    public void endArray() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    @Override
    public void emptyValue() {
        if (topIsObject()) {
            top().setNix(key);
        } else {
            top().addNix();
        }
    }

    @Override
    public void booleanValue(boolean v) {
        if (topIsObject()) {
            top().setBool(key, v);
        } else {
            top().addBool(v);
        }
    }

    @Override
    public void longValue(long v) {
        if (topIsObject()) {
            top().setLong(key, v);
        } else {
            top().addLong(v);
        }
    }

    @Override
    public void doubleValue(double v) {
        if (topIsObject()) {
            top().setDouble(key, v);
        } else {
            top().addDouble(v);
        }
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

        if (topIsObject()) {
            top().setString(key, v);
        } else {
            top().addString(v);
        }
    }

    @Override
    public void dataValue(byte[] data) {
        if (topIsObject()) {
            top().setData(key, data);
        } else {
            top().addData(data);
        }
    }
}
