// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable file reference that can only be created from classes within the same package.
 * This is to prevent clients from creating arbitrary and invalid file references.
 *
 * @author Tony Vaagenes
 */
public final class FileReference {

    private final String value;

    public FileReference(String value) {
        Objects.requireNonNull(value);
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FileReference &&
                value.equals(((FileReference)other).value);
    }

    @Override
    public String toString() {
        return "file '" + value + "'";
    }

    public static List<String> toValues(Collection<FileReference> references) {
        List<String> ret = new ArrayList<String>();
        for (FileReference r: references) {
            ret.add(r.value());
        }
        return ret;
    }

    public static Map<String, String> toValueMap(Map<String, FileReference> map) {
        Map<String, String> ret = new LinkedHashMap<>();
        for (Map.Entry<String, FileReference> e : map.entrySet()) {
            ret.put(e.getKey(), e.getValue().value());
        }
        return ret;
    }

    public static FileReference mockFileReferenceForUnitTesting(File file) {
        if (! file.exists())
            throw new IllegalArgumentException("File '" + file.getAbsolutePath() + "' does not exist.");
        return new FileReference(file.getPath());
    }

}
