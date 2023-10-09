// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

import java.util.List;

/**
 * Defines an interface for the name server lookup.
 *
 * @author Simon Thoresen Hult
 */
public interface IMirror {

    /**
     * Obtain all the services matching a given pattern.
     *
     * The pattern is matched against all service names in the local mirror repository. A service name may contain '/'
     * as a separator token. A pattern may contain '*' to match anything up to the next '/' (or the end of the
     * name). This means that the pattern 'foo/<!-- slash-star -->*<!-- star-slash -->/baz' would match the service
     * names 'foo/bar/baz' and 'foo/xyz/baz'. The pattern 'foo/b*' would match 'foo/bar', but neither 'foo/xyz' nor
     * 'foo/bar/baz'. The pattern 'a*b' will never match anything.
     * As a special case, a pattern can end in '**' to match the rest of a name including '/' separators.
     *
     * @return a list of all matching services, with corresponding connect specs
     * @param pattern The pattern used for matching
     **/
    List<Mirror.Entry> lookup(String pattern);

    /**
     * Obtain the number of updates seen by this mirror. The value may wrap, but will never become 0 again. This can be
     * used for name lookup optimization, because the results returned by lookup() will never change unless this number
     * also changes.
     *
     * @return number of slobrok updates seen
     **/
    int updates();

}
