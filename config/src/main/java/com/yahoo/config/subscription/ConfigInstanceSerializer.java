// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.config.Serializer;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;

/**
 * Implements a config instance serializer, serializing a config instance to a slime object.
 *
 * @author Ulf Lilleengen
 */
public class ConfigInstanceSerializer implements Serializer {
    private final Slime slime;
    private final Cursor root;
    public ConfigInstanceSerializer(Slime slime) {
        this.slime = slime;
        root = slime.setObject();
    }

    public ConfigInstanceSerializer(Slime slime, Cursor root) {
        this.slime = slime;
        this.root = root;
    }

    @Override
    public Serializer createInner(String name) {
        Cursor childRoot = root.setObject(name);
        return new ConfigInstanceSerializer(slime, childRoot);
    }

    @Override
    public Serializer createArray(String name) {
        return new ConfigInstanceSerializer(slime, root.setArray(name));
    }

    @Override
    public Serializer createInner() {
        return new ConfigInstanceSerializer(slime, root.addObject());
    }

    @Override
    public Serializer createMap(String name) {
        return createInner(name);
    }

    public void serialize(String name, boolean value) {
        root.setBool(name, value);
    }

    public void serialize(String name, double value) {
        root.setDouble(name, value);
    }

    public void serialize(String name, int value) {
        root.setLong(name, value);
    }

    public void serialize(String name, long value) {
        root.setLong(name, value);
    }

    public void serialize(String name, String value) {
        root.setString(name, value);
    }

    @Override
    public void serialize(boolean value) {
        root.addBool(value);
    }

    @Override
    public void serialize(double value) {
        root.addDouble(value);
    }

    @Override
    public void serialize(long value) {
        root.addLong(value);
    }

    @Override
    public void serialize(int value) {
        root.addLong(value);
    }

    @Override
    public void serialize(String value) {
        root.addString(value);
    }

}
