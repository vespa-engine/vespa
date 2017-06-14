// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.test.TotalOrderTester;
import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;
import com.google.common.testing.EqualsTester;

/**
 * @author Vegard Sjonfjell
 * @since 5.39
 */
public class VersionTest {
    @Test
    public void testConstructFromIntegers() {
        Version exampleVersion = Version.fromIntValues(3, 2, 1);
        assertThat(exampleVersion.getMajor(), is(3));
        assertThat(exampleVersion.getMinor(), is(2));
        assertThat(exampleVersion.getMicro(), is(1));
    }

    @Test (expected = IllegalArgumentException.class)
    public void testConstructFromIntegersNegativesShouldFail() throws IllegalArgumentException {
        Version.fromIntValues(2, -1, 1);
    }

    @Test (expected = IllegalArgumentException.class)
    public void testConstructFromStringTooLongVersionStringShouldFail() throws IllegalArgumentException {
        Version.fromString("3.2.1.4");
    }

    @Test (expected = IllegalArgumentException.class)
    public void testConstructFromStringTooShortVersionStringShouldFail() throws IllegalArgumentException {
        Version.fromString("3.2");
    }

    @Test (expected = IllegalArgumentException.class)
    public void testConstructFromStringInvalidVersionStringShouldFail() throws IllegalArgumentException {
        Version.fromString("4.34.3a");
    }

    @Test
    public void testEncodeToStringRepresentation() {
        assertThat(Version.fromIntValues(3, 2, 1).toSerializedForm(), is("3.2.1"));
        assertThat(Version.fromIntValues(0, 0, 0).toSerializedForm(), is("0.0.0"));
    }

    @Test
    public void testEqualityAndHashCode() {
        new EqualsTester()
                .addEqualityGroup(Version.fromIntValues(3, 2, 1), Version.fromIntValues(3, 2, 1))
                .addEqualityGroup(Version.fromIntValues(1, 2, 3), Version.fromString("1.2.3"))
                .addEqualityGroup(Version.fromString("1.5.1"))
                .addEqualityGroup(Version.fromIntValues(1, 2, 1))
                .addEqualityGroup(Version.fromString("0.0.0"))
                .testEquals();
    }

    @Test
    public void testCompareTo() {
        new TotalOrderTester<Version>()
                .theseObjects(Version.fromIntValues(1, 1, 1), Version.fromIntValues(1, 1, 1))
                .areLessThan(Version.fromIntValues(2, 1, 1))
                .areLessThan(Version.fromIntValues(2, 2, 1))
                .areLessThan(Version.fromIntValues(2, 2, 2))
                .areLessThan(Version.fromIntValues(3, 0, 0))
                .testOrdering();
    }
}
