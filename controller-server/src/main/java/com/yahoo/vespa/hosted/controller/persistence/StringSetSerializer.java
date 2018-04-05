// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Serialization of a set of strings to/from Json bytes using Slime.
 *
 * The set is serialized as an array of string.
 *
 * @author bratseth
 */
public class StringSetSerializer {

    public byte[] toJson(Set<String> stringSet) {
        try {
            Slime slime = new Slime();
            Cursor array = slime.setArray();
            for (String element : stringSet)
                array.addString(element);
            return SlimeUtils.toJsonBytes(slime);
        }
        catch (IOException e) {
            throw new RuntimeException("Serialization of a string set failed", e);
        }

    }

    public Set<String> fromSlime(Slime slime) {
        Inspector inspector = slime.get();
        Set<String> stringSet = new HashSet<>();
        inspector.traverse((ArrayTraverser) (index, name) -> stringSet.add(name.asString()));
        return stringSet;
    }

}
