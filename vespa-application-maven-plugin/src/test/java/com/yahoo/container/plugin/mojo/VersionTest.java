package com.yahoo.container.plugin.mojo;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class VersionTest {

    @Test
    public void test() {
        assertEquals("1.2.0", Version.from("1.2.0").toString());
        assertEquals("3-SNAPSHOT", Version.from("3-SNAPSHOT").toString());

        List<Version> versions = List.of(Version.of(1, 2, 3),
                                         Version.of(1, 2, 4),
                                         Version.of(1, 3, 2),
                                         Version.ofSnapshot(1),
                                         Version.of(2, 1, 0));

        for (int i = 0; i < versions.size(); i++)
            for (int j = 0; j < versions.size(); j++)
                assertEquals(versions.get(i) + " should be less than " + versions.get(j),
                             Integer.compare(i, j), versions.get(i).compareTo(versions.get(j)));
    }

}
