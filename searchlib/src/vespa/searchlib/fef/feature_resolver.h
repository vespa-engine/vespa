// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "featureexecutor.h"

namespace vespalib::eval {
class ValueType;
}

namespace search::fef {

/**
 * A FeatureResolver knowns the name and memory location of values
 * calculated by a RankProgram. Note that objects of this class will
 * reference data owned by the RankProgram used to create it.
 **/
class FeatureResolver {
private:
    std::vector<std::string>                      _names;
    std::vector<LazyValue>                        _features;
    std::vector<bool>                             _is_object;
    std::vector<const vespalib::eval::ValueType*> _types;

public:
    FeatureResolver(size_t size_hint);
    ~FeatureResolver();
    void add(const std::string& name, LazyValue feature, bool is_object,
             const vespalib::eval::ValueType* type = nullptr) {
        _names.push_back(name);
        _features.push_back(feature);
        _is_object.push_back(is_object);
        _types.push_back(type);
    }
    size_t num_features() const { return _names.size(); }
    const std::string& name_of(size_t i) const { return _names[i]; }
    bool is_object(size_t i) const { return _is_object[i]; }
    // The type of an object feature, or nullptr for a number or an unknown type.
    const vespalib::eval::ValueType* type_of(size_t i) const { return _types[i]; }
    LazyValue resolve(size_t i) const { return _features[i]; }
};

} // namespace search::fef
