// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "configpayload.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/data/slime/inspector.h>

namespace config::internal {

void requireValid(vespalib::stringref __fieldName, const ::vespalib::slime::Inspector & __inspector);

template<typename T>
T convertValue(const ::vespalib::slime::Inspector & __inspector) { return T(::config::ConfigPayload(__inspector)); }

template<>
int32_t convertValue(const ::vespalib::slime::Inspector & __inspector);

template<>
int64_t convertValue(const ::vespalib::slime::Inspector & __inspector);

template<>
double convertValue(const ::vespalib::slime::Inspector & __inspector);

template<>
bool convertValue(const ::vespalib::slime::Inspector & __inspector);

template<>
vespalib::string convertValue(const ::vespalib::slime::Inspector & __inspector);

template<typename T>
struct ValueConverter {
    T operator()(vespalib::stringref __fieldName, const ::vespalib::slime::Inspector & __inspector) {
        requireValid(__fieldName, __inspector);
        return convertValue<T>(__inspector);
    }
    T operator()(const ::vespalib::slime::Inspector & __inspector) {
        return __inspector.valid() ? convertValue<T>(__inspector) : T();
    }
    T operator()(const ::vespalib::slime::Inspector & __inspector, T __t) {
        return __inspector.valid() ? convertValue<T>(__inspector) : __t;
    }
};

}
