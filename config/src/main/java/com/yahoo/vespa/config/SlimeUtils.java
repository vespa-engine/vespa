// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.slime.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Extra utilities/operations on slime trees that we would like to have as part of slime in the future, but
 * which resides here until we have a better place to put it.
 *
 * @author Ulf Lilleengen
 */
public class SlimeUtils {

    public static void copyObject(Inspector from, Cursor to) {
        if (from.type() != Type.OBJECT) {
            throw new IllegalArgumentException("Cannot copy object: " + from);
        }
        from.traverse(new ObjectTraverser() {
            @Override
            public void field(String name, Inspector inspector) {
                setObjectEntry(inspector, name, to);
            }
        });

    }

    private static void setObjectEntry(Inspector from, String name, Cursor to) {
        switch (from.type()) {
            case NIX:
                to.setNix(name);
                break;
            case BOOL:
                to.setBool(name, from.asBool());
                break;
            case LONG:
                to.setLong(name, from.asLong());
                break;
            case DOUBLE:
                to.setDouble(name, from.asDouble());
                break;
            case STRING:
                to.setString(name, from.asString());
                break;
            case DATA:
                to.setData(name, from.asData());
                break;
            case ARRAY:
                Cursor array = to.setArray(name);
                copyArray(from, array);
                break;
            case OBJECT:
                Cursor object = to.setObject(name);
                copyObject(from, object);
                break;
        }
    }

    private static void copyArray(Inspector from, final Cursor to) {
        from.traverse(new ArrayTraverser() {
            @Override
            public void entry(int i, Inspector inspector) {
                addValue(inspector, to);
            }
        });

    }

    private static void addValue(Inspector from, Cursor to) {
        switch (from.type()) {
            case NIX:
                to.addNix();
                break;
            case BOOL:
                to.addBool(from.asBool());
                break;
            case LONG:
                to.addLong(from.asLong());
                break;
            case DOUBLE:
                to.addDouble(from.asDouble());
                break;
            case STRING:
                to.addString(from.asString());
                break;
            case DATA:
                to.addData(from.asData());
                break;
            case ARRAY:
                Cursor array = to.addArray();
                copyArray(from, array);
                break;
            case OBJECT:
                Cursor object = to.addObject();
                copyObject(from, object);
                break;
        }

    }

    public static byte[] toJsonBytes(Slime slime) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new JsonFormat(true).encode(baos, slime);
        return baos.toByteArray();
    }

    public static Slime jsonToSlime(byte[] json) {
        Slime slime = new Slime();
        new JsonDecoder().decode(slime, json);
        return slime;
    }

    public static Optional<String> optionalString(Inspector inspector) {
        return Optional.of(inspector.asString()).filter(s -> !s.isEmpty());
    }
}
