// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <memory>

namespace search::fef {

/**
 * The full type of a feature calculated by the ranking framework. The
 * ranking framework wraps a thin layer on top of the types defined in
 * the low-level eval library. A feature can either be a simple number
 * represented by a double or a polymorph value represented with an
 * object. The ranking framework itself will mostly care about the
 * representation (number/object) and not the specific type. The type
 * function is used to extract the underlying type and is only allowed
 * for features that are objects.
 **/
class FeatureType {
private:
    using TYPE = vespalib::eval::ValueType;
    using TYPE_UP = std::unique_ptr<TYPE>;
    TYPE_UP _type;
    static const FeatureType _number;
    FeatureType(TYPE_UP type_in) : _type(std::move(type_in)) {}
public:
    FeatureType(const FeatureType &rhs);
    FeatureType(FeatureType &&rhs) = default;
    bool is_object() const { return (_type.get() != nullptr); }
    const TYPE &type() const;
    static const FeatureType &number() { return _number; }
    static FeatureType object(const TYPE &type_in);
};

}
