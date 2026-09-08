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
        if (containsControlCharacter(value))
            throw new IllegalArgumentException("File reference value may not contain control characters, but got '" + value + "'");
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

    /**
     * Returns true if the string contains any control characters (0x00-0x1F and 0x7F), false otherwise.
     */
    private static boolean containsControlCharacter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) return true;
        }
        return false;
    }

}
