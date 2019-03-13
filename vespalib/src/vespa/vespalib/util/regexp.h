// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/noncopyable.hpp>

namespace vespalib {

/**
 * Utility class for simple regular expression matching.
 * This class wraps the C library implementation of
 * the posix regex API, for simple and easy usage.
 * Note: also minimizes namespace pollution, you don't
 * need to include <regex.h> when using class.
 **/
class Regexp : public noncopyable
{
public:
    class Flags {
    public:
        /**
         * By default enable posix extended regex.
         **/
        Flags();
        /**
         * Enable case insentive search.
         **/
        Flags & enableICASE();
        /**
         * Return the decoded set of flags for the implementation.
         **/
        unsigned long flags() const { return _flags; }
    private:
        unsigned long _flags;
    };
    /**
     * Construct from a Posix Extended regular expression.
     * @throw IllegalArgumentException if the RE is invalid.
     * @param re Regular expression.
     **/
    Regexp(vespalib::stringref re, Flags=Flags());

    ~Regexp();

    /**
     * Will tell if the regexp was valid.
     * @return true if regexp is valid.
     **/
    bool valid() const { return _valid; }

    /**
     * Check if the given string is matched by this regexp.
     * If called on invalid regexp it will return false.
     * @param s text to search for a match.
     * @return true if a match was found.
     **/
    bool match(vespalib::stringref s) const;

    /**
     * Look at the given regular expression and identify the prefix
     * that must be present for a string to match it. Note that an
     * un-anchored expression will have an empty prefix. Also note
     * that this function is simple and might underestimate the actual
     * size of the prefix.
     *
     * @param re Regular expression.
     * @return prefix that must be present in matching strings
     **/
    static vespalib::string get_prefix(vespalib::stringref re);

    /**
     * Make a regexp matching strings with the given prefix.
     *
     * @param prefix the prefix
     * @return the regexp
     **/
    static vespalib::string make_from_prefix(vespalib::stringref prefix);

    /**
     * Make a regexp matching strings with the given suffix.
     *
     * @param suffix the suffix
     * @return the regexp
     **/
    static vespalib::string make_from_suffix(vespalib::stringref suffix);

    /**
     * Make a regexp matching strings with the given substring.
     *
     * @param substring the substring
     * @return the regexp
     **/
    static vespalib::string make_from_substring(vespalib::stringref substring);

private:
    bool _valid;
    void *_data;
    bool compile(vespalib::stringref re, Flags flags);
};

} // namespace vespalib
