// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/string.h>

namespace document::select {

using VariableMapT = vespalib::hash_map<vespalib::string, double>;

class VariableMap : public VariableMapT {
public:
    using VariableMapT::VariableMapT;
};

}
