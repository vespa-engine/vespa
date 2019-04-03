// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A very simple template engine when there's little complexity and lots of Velocity special characters $ and #,
 * i.e. typically shell script.
 *
 * @author hakonhall
 */
public class Templar {
    private final String template;

    private String prefix = "<%=";
    private String suffix = "%>";

    private final Map<String, String> settings = new HashMap<>();

    public static Templar fromUtf8File(Path path) {
        return new Templar(new UnixPath(path).readUtf8File());
    }

    public Templar(String template) {
        this.template = template;
    }

    public Templar set(String name, String value) {
        settings.put(name, value);
        return this;
    }

    public String resolve() {
        StringBuilder text = new StringBuilder(template.length() * 2);

        int start= 0;
        int end;

        for (; start < template.length(); start = end) {
            int prefixStart = template.indexOf(prefix, start);


            if (prefixStart == -1) {
                text.append(template, start, template.length());
                break;
            } else {
                text.append(template, start, prefixStart);
            }

            int suffixStart = template.indexOf(suffix, prefixStart + prefix.length());
            if (suffixStart == -1) {
                throw new IllegalArgumentException("Prefix at offset " + prefixStart + " is not terminated");
            }

            int prefixEnd = prefixStart + prefix.length();
            String name = template.substring(prefixEnd, suffixStart).trim();
            String value = settings.get(name);
            if (value == null) {
                throw new IllegalArgumentException("No value is set for name '" + name + "' at offset " + prefixEnd);
            }

            text.append(value);

            end = suffixStart + suffix.length();
        }

        return text.toString();
    }

    public FileWriter getFileWriterTo(Path path) {
        return new FileWriter(path, this::resolve);
    }
}
