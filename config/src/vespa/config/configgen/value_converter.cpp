// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "value_converter.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace vespalib::slime;

namespace config::internal {

template<>
int32_t convertValue(const ::vespalib::slime::Inspector & __inspector) {
    switch (__inspector.type().getId()) {
        case LONG::ID:   return static_cast<int32_t>(__inspector.asLong());
        case DOUBLE::ID: return static_cast<int32_t>(__inspector.asDouble());
        case STRING::ID: return static_cast<int32_t>(strtoll(__inspector.asString().make_string().c_str(), 0, 0));
    }
    throw InvalidConfigException(make_string("Expected int32_t, but got incompatible config type %u", __inspector.type().getId()));
}

template<>
int64_t convertValue(const ::vespalib::slime::Inspector & __inspector) {
    switch (__inspector.type().getId()) {
        case LONG::ID:   return static_cast<int64_t>(__inspector.asLong());
        case DOUBLE::ID: return static_cast<int64_t>(__inspector.asDouble());
        case STRING::ID: return static_cast<int64_t>(strtoll(__inspector.asString().make_string().c_str(), 0, 0));
    }
    throw InvalidConfigException(make_string("Expected int64_t, but got incompatible config type %u", __inspector.type().getId()));
}

template<>
double convertValue(const ::vespalib::slime::Inspector & __inspector) {
    switch (__inspector.type().getId()) {
        case LONG::ID:   return static_cast<double>(__inspector.asLong());
        case DOUBLE::ID: return static_cast<double>(__inspector.asDouble());
        case STRING::ID: return static_cast<double>(vespalib::locale::c::strtod(__inspector.asString().make_string().c_str(), 0));
    }
    throw InvalidConfigException(make_string("Expected double, but got incompatible config type %u", __inspector.type().getId()));
}

template<>
bool convertValue(const ::vespalib::slime::Inspector & __inspector) {
    switch (__inspector.type().getId()) {
        case BOOL::ID:   return __inspector.asBool();
        case STRING::ID:
            vespalib::string s(__inspector.asString().make_string());
            return s.compare("true") == 0 ? true : false;
    }
    throw InvalidConfigException(make_string("Expected bool, but got incompatible config type %u", __inspector.type().getId()));
}

template<>
vespalib::string convertValue(const ::vespalib::slime::Inspector & __inspector) { return __inspector.asString().make_string(); }

void
requireValid(vespalib::stringref __fieldName, const ::vespalib::slime::Inspector & __inspector) {
    if (!__inspector.valid()) {
        throw ::config::InvalidConfigException("Value for '" + __fieldName + "' required but not found");
    }
}

}

