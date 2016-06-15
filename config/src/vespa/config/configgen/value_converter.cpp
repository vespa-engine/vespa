// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config/common/exceptions.h>
#include "value_converter.h"

using namespace vespalib;
using namespace vespalib::slime;

namespace config {

namespace internal {

template<>
int32_t convertValue(const ::vespalib::slime::Inspector & __inspector) {
    switch (__inspector.type().getId()) {
        case LONG::ID:   return static_cast<int32_t>(__inspector.asLong());
        case DOUBLE::ID: return static_cast<int32_t>(__inspector.asDouble());
        case STRING::ID: return static_cast<int32_t>(strtoll(__inspector.asString().make_string().c_str(), 0, 0));
    }
    throw InvalidConfigException("Expected int32_t, but got incompatible config type " + __inspector.type().getId());
}

template<>
int64_t convertValue(const ::vespalib::slime::Inspector & __inspector) {
    switch (__inspector.type().getId()) {
        case LONG::ID:   return static_cast<int64_t>(__inspector.asLong());
        case DOUBLE::ID: return static_cast<int64_t>(__inspector.asDouble());
        case STRING::ID: return static_cast<int64_t>(strtoll(__inspector.asString().make_string().c_str(), 0, 0));
    }
    throw InvalidConfigException("Expected int64_t, but got incompatible config type " + __inspector.type().getId());
}

template<>
double convertValue(const ::vespalib::slime::Inspector & __inspector) {
    switch (__inspector.type().getId()) {
        case LONG::ID:   return static_cast<double>(__inspector.asLong());
        case DOUBLE::ID: return static_cast<double>(__inspector.asDouble());
        case STRING::ID: return static_cast<double>(strtod(__inspector.asString().make_string().c_str(), 0));
    }
    throw InvalidConfigException("Expected double, but got incompatible config type " + __inspector.type().getId());
}

template<>
bool convertValue(const ::vespalib::slime::Inspector & __inspector) {
    switch (__inspector.type().getId()) {
        case BOOL::ID:   return __inspector.asBool();
        case STRING::ID:
            vespalib::string s(__inspector.asString().make_string());
            return s.compare("true") == 0 ? true : false;
    }
    throw InvalidConfigException("Expected bool, but got incompatible config type " + __inspector.type().getId());
}

template<>
vespalib::string convertValue(const ::vespalib::slime::Inspector & __inspector) { return __inspector.asString().make_string(); }

void
requireValid(const vespalib::string & __fieldName, const ::vespalib::slime::Inspector & __inspector) {
    if (!__inspector.valid()) {
        throw ::config::InvalidConfigException("Value for '" + __fieldName + "' required but not found");
    }
}

}

}
