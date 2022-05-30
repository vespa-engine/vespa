// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/hash_map.h>

/** A map from strings to strings where all storage are maintained internally
 * a la perl assoc.arrays
 */

class Fast_StringMap
{
private:
    using Map = vespalib::hash_map<vespalib::string, vespalib::string>;
    Map  _backing;
public:
    void Insert(const char* key, const char* value);
    const char *Lookup(const char* key, const char* defval) const;
};

