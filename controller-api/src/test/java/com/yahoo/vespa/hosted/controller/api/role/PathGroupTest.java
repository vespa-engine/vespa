// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.role;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author jonmv
 * @author mpolden
 */
public class PathGroupTest {

    @Test
    void uniqueMatches() {
        // Ensure that each path group contains at most one match for any given path, to avoid undefined context extraction.
        Set<String> checkedAgainstSelf = new HashSet<>();
        for (PathGroup group1 : PathGroup.values())
            for (PathGroup group2 : PathGroup.values())
                for (String path1 : group1.pathSpecs)
                    for (String path2 : group2.pathSpecs) {
                        if (path1.equals(path2)) {
                            if (checkedAgainstSelf.add(path1)) continue;
                            fail("Path '" + path1 + "' appears in both '" + group1 + "' and '" + group2 + "'.");
                        }

                        String[] parts1 = path1.split("/");
                        String[] parts2 = path2.split("/");

                        int end = Math.min(parts1.length, parts2.length);
                        // If one path has more parts than the other ...
                        // and the other doesn't end with a wildcard matcher ...
                        // and the longest one isn't just one wildcard longer ...
                        // then one is strictly longer than the other, and it's not a match.
                        if (end < parts1.length && (end == 0 || !parts2[end - 1].equals("{*}")) && !parts1[end].equals("{*}")) continue;
                        if (end < parts2.length && (end == 0 || !parts1[end - 1].equals("{*}")) && !parts2[end].equals("{*}")) continue;

                        int i;
                        for (i = 0; i < end; i++)
                            if (   !  parts1[i].equals(parts2[i])
                                    && !(parts1[i].startsWith("{") && parts1[i].endsWith("}"))
                                    && !(parts2[i].startsWith("{") && parts2[i].endsWith("}"))) break;

                        if (i == end) fail("Paths '" + path1 + "' and '" + path2 + "' overlap.");
                    }

        assertEquals(PathGroup.all().stream().mapToInt(group -> group.pathSpecs.size()).sum(),
                checkedAgainstSelf.size());
    }

    @Test
    void contextMatches() {
        for (PathGroup group : PathGroup.values())
            for (String spec : group.pathSpecs) {
                for (PathGroup.Matcher matcher : PathGroup.Matcher.values()) {
                    if (group.matchers.contains(matcher)) {
                        if (!spec.contains(matcher.pattern))
                            fail("Spec '" + spec + "' in '" + group.name() + "' should contain matcher '" + matcher.pattern + "'.");
                        if (spec.replaceFirst(Pattern.quote(matcher.pattern), "").contains(matcher.pattern))
                            fail("Spec '" + spec + "' in '" + group.name() + "' contains more than one instance of '" + matcher.pattern + "'.");
                    }
                    else if (spec.contains(matcher.pattern))
                        fail("Spec '" + spec + "' in '" + group.name() + "' should not contain matcher '" + matcher.pattern + "'.");
                }
            }
    }

}
