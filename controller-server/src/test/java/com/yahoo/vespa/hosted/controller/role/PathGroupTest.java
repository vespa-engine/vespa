package com.yahoo.vespa.hosted.controller.role;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

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
                if (!overlapping.isEmpty()) {
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
                    if (end < parts1.length && ! parts2[end - 1].equals("{*}") && ! parts1[end].equals("{*}")) continue;
                    if (end < parts2.length && ! parts1[end - 1].equals("{*}") && ! parts2[end].equals("{*}")) continue;

                    int i;
                    for (i = 0; i < end; i++)
                        if (   !  parts1[i].equals(parts2[i])
                            && ! (parts1[i].startsWith("{") && parts1[i].endsWith("}"))
                            && ! (parts2[i].startsWith("{") && parts2[i].endsWith("}"))) break;

                    if (i == end) fail("Paths '" + path1 + "' and '" + path2 + "' overlap.");
                }
    }

}
