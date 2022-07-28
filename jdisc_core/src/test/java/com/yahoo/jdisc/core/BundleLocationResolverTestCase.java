// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class BundleLocationResolverTestCase {

    @Test
    void requireThatDollarsAreIncludedInLocation() {
        assertLocation("scheme:$foo", "scheme:$foo");
        assertLocation("scheme:foo$bar", "scheme:foo$bar");
    }

    @Test
    void requireThatCurlyBracesAreIncludedInLocation() {
        assertLocation("scheme:{foo", "scheme:{foo");
        assertLocation("scheme:foo{", "scheme:foo{");
        assertLocation("scheme:foo{bar", "scheme:foo{bar");
        assertLocation("scheme:}foo", "scheme:}foo");
        assertLocation("scheme:foo}", "scheme:foo}");
        assertLocation("scheme:foo}bar", "scheme:foo}bar");
        assertLocation("scheme:{foo}bar", "scheme:{foo}bar");
        assertLocation("scheme:foo{bar}", "scheme:foo{bar}");
    }

    @Test
    void requireThatUnterminatedPropertiesAreIncludedInLocation() {
        assertLocation("scheme:${foo", "scheme:${foo");
        assertLocation("scheme:foo${", "scheme:foo${");
        assertLocation("scheme:foo${bar", "scheme:foo${bar");
    }

    @Test
    void requireThatAllSystemPropertiesAreExpanded() throws IOException {
        assertCanonicalPath("", "${foo}");
        assertCanonicalPath("barcox", "${foo}bar${baz}cox");
        assertCanonicalPath("foobaz", "foo${bar}baz${cox}");

        System.setProperty("requireThatAllSystemPropertiesAreExpanded.foo", "FOO");
        System.setProperty("requireThatAllSystemPropertiesAreExpanded.bar", "BAR");
        System.setProperty("requireThatAllSystemPropertiesAreExpanded.baz", "BAZ");
        System.setProperty("requireThatAllSystemPropertiesAreExpanded.cox", "COX");
        assertCanonicalPath("FOO", "${requireThatAllSystemPropertiesAreExpanded.foo}");
        assertCanonicalPath("FOObarBAZcox", "${requireThatAllSystemPropertiesAreExpanded.foo}bar" +
                "${requireThatAllSystemPropertiesAreExpanded.baz}cox");
        assertCanonicalPath("fooBARbazCOX", "foo${requireThatAllSystemPropertiesAreExpanded.bar}" +
                "baz${requireThatAllSystemPropertiesAreExpanded.cox}");
    }

    @Test
    void requireThatUnschemedLocationsAreExpandedToBundleLocationProperty() throws IOException {
        assertCanonicalPath(BundleLocationResolver.BUNDLE_PATH + "foo", "foo");
    }

    @Test
    void requireThatFileSchemedLocationsAreCanonicalized() throws IOException {
        assertCanonicalPath("", "file:");
        assertCanonicalPath("foo", "file:foo");
        assertCanonicalPath("foo", "file:./foo");
        assertCanonicalPath("foo/bar", "file:foo/bar");
        assertCanonicalPath("foo/bar", "file:./foo/../foo/./bar");
        assertCanonicalPath("foo", " \f\n\r\tfile:foo");
    }

    @Test
    void requireThatOtherSchemedLocationsAreUntouched() {
        assertLocation("foo:", "foo:");
        assertLocation("foo:bar", "foo:bar");
        assertLocation("foo:bar/baz", "foo:bar/baz");
    }

    private static void assertCanonicalPath(String expected, String bundleLocation) throws IOException {
        assertLocation("file:" + new File(expected).getCanonicalPath(), bundleLocation);
    }

    private static void assertLocation(String expected, String bundleLocation) {
        assertEquals(expected, BundleLocationResolver.resolve(bundleLocation));
    }
}
