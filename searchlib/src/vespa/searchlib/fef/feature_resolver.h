// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "number_or_object.h"
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include "featureexecutor.h"

namespace search {
namespace fef {

/**
 * A FeatureResolver knowns the name and memory location of values
 * calculated by a RankProgram. Note that objects of this class will
 * reference data owned by the RankProgram used to create it.
 **/
class FeatureResolver
{
private:
    std::vector<vespalib::string> _names;
    std::vector<LazyValue> _features;
    std::vector<bool> _is_object;
public:
    FeatureResolver(size_t size_hint);
    ~FeatureResolver();
    void add(const vespalib::string &name, LazyValue feature, bool is_object) {
        _names.push_back(name);
        _features.push_back(feature);
        _is_object.push_back(is_object);
    }
    size_t num_features() const { return _names.size(); }
    const vespalib::string &name_of(size_t i) const { return _names[i]; }
    bool is_object(size_t i) const { return _is_object[i]; }
    LazyValue resolve(size_t i) const { return _features[i]; }
};

} // namespace fef
} // namespace search
