// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;

public class FS4Properties {
    private String name;

    static public class Entry {
        public final String key;
        private final byte [] val;
        public Entry(byte[] k, byte[] v) {
            key = Utf8.toString(k);
            val = v;
        }
        public final String getValueString() { return Utf8.toString(val); }
        public final byte [] getValue() { return val; }
    };

    private Entry[] entries;

    void decode(ByteBuffer buffer) {
        int nameLen = buffer.getInt();
        byte[] utf8name = new byte[nameLen];
        buffer.get(utf8name);
        this.setName(Utf8.toString(utf8name));

        int n = buffer.getInt();
        setEntries(new Entry[n]);
        for (int j = 0; j < n; j++) {
            int keyLen = buffer.getInt();
            byte[] key = new byte[keyLen];
            buffer.get(key);

            int valLen = buffer.getInt();
            byte[] value = new byte[valLen];
            buffer.get(value);

            getEntries()[j] = new Entry(key, value);
        }
    }

    public Entry[] getEntries() {
        return entries;
    }

    public void setEntries(Entry[] entries) {
        this.entries = entries;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
