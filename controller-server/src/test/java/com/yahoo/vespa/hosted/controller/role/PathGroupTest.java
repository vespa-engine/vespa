package com.yahoo.vespa.hosted.controller.role;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.fail;

/**
 * @author jonmv
 * @author mpolden
 */
public class PathGroupTest {

    @Test
    public void nonOverlappingGroups() {
        for (PathGroup pg : PathGroup.all()) {
            for (PathGroup pg2 : PathGroup.all()) {
                if (pg == pg2) continue;
                Set<String> overlapping = new LinkedHashSet<>(pg.pathSpecs);
                overlapping.retainAll(pg2.pathSpecs);
                if ( ! overlapping.isEmpty()) {
                    fail("The following path specs overlap in " + pg + " and " + pg2 + ": " + overlapping);
                }
            }
        }
    }

    @Test
    public void uniqueMatches() {
        // Ensure that each path group contains at most one match for any given path, to avoid undefined context extraction.
        for (PathGroup group : PathGroup.values())
            for (String path1 : group.pathSpecs)
                for (String path2 : group.pathSpecs) {
                    if (path1 == path2) continue;

                    String[] parts1 = path1.split("/");
                    String[] parts2 = path2.split("/");

                    int end = Math.min(parts1.length, parts2.length);
                    // If one path has more parts than the other ...
                    // and the other doesn't end with a wildcard matcher ...
                    // and the longest one isn't just one part longer, which is a wildcard ...
                    if (end < parts1.length && (end == 0 || ! parts2[end - 1].equals("{*}")) && ! parts1[end].equals("{*}")) continue;
                    if (end < parts2.length && (end == 0 || ! parts1[end - 1].equals("{*}")) && ! parts2[end].equals("{*}")) continue;

                    int i;
                    for (i = 0; i < end; i++)
                        if (   !  parts1[i].equals(parts2[i])
                            && ! (parts1[i].startsWith("{") && parts1[i].endsWith("}"))
                            && ! (parts2[i].startsWith("{") && parts2[i].endsWith("}"))) break;

                    if (i == end) fail("Paths '" + path1 + "' and '" + path2 + "' overlap.");
                }
    }

    @Test
    public void contextMatches() {
        for (PathGroup group : PathGroup.values())
            for (String spec : group.pathSpecs) {
                for (PathGroup.Matcher matcher : PathGroup.Matcher.values()) {
                    if (group.matchers.contains(matcher)) {
                        if ( ! spec.contains(matcher.pattern))
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
