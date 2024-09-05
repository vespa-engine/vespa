// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/hash_map.h>
#include <string>

namespace document::select {

using VariableMapT = vespalib::hash_map<std::string, double>;

class VariableMap : public VariableMapT {
public:
    using VariableMapT::VariableMapT;
};

}
