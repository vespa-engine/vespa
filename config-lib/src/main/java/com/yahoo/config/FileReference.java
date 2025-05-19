// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable file reference.
 *
 * @author Tony Vaagenes
 */
public record FileReference(String value) {

    public FileReference {
        Objects.requireNonNull(value);
        if (Path.of(value).normalize().startsWith(".."))
            throw new IllegalArgumentException("Path may not start with '..' but got '" + value + "'");
    }

    @Override
    public String toString() {
        return "file '" + value + "'";
    }

    public static List<String> toValues(Collection<FileReference> references) {
        List<String> ret = new ArrayList<>();
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
