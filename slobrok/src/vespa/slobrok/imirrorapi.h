// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace slobrok::api {

/**
 * @brief Defines an interface for the name server lookup.
 **/
class IMirrorAPI {
protected:
    static bool match(const char *name, const char *pattern);

public:
    /**
     * @brief Release any allocated resources.
     **/
    virtual ~IMirrorAPI() { }

    /**
     * @brief pair of <name, connectionspec>.
     *
     * The first element of pair is a string containing the service name.
     * The second is the connection spec, typically "tcp/foo.bar.com:42"
     **/
    using Spec = std::pair<std::string, std::string>;

    /**
     * @brief vector of <name, connectionspec> pairs.
     *
     * The first element of each pair is a string containing the service name.
     * The second is the connection spec, typically "tcp/foo.bar.com:42"
     **/
    using SpecList = std::vector<Spec>;

    /**
     * Obtain all the services matching a given pattern.
     *
     * The pattern is matched against all service names in the local mirror repository. A service name may contain '/'
     * as a separator token. A pattern may contain '*' to match anything up to the next '/' (or the end of the
     * name). This means that the pattern 'foo/<!-- slash-star -->*<!-- star-slash -->/baz' would match the service
     * names 'foo/bar/baz' and 'foo/xyz/baz'. The pattern 'foo/b*' would match 'foo/bar', but neither 'foo/xyz' nor
     * 'foo/bar/baz'. The pattern 'a*b' will never match anything.
     *
     * @return a list of all matching services, with corresponding connect specs
     * @param pattern The pattern used for matching
     **/
    virtual SpecList lookup(vespalib::stringref pattern) const = 0;

    /**
     * Obtain the number of updates seen by this mirror. The value may wrap, but will never become 0 again. This can be
     * used for name lookup optimization, because the results returned by lookup() will never change unless this number
     * also changes.
     *
     * @return number of slobrok updates seen
     **/
    virtual uint32_t updates() const = 0;

    virtual bool ready() const = 0;
};

}
