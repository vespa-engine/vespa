// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/**
 * Utility class inspecting and generating regular expression strings.
 **/
class RegexpUtil
{
public:
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
};

} // namespace vespalib
