// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "countmetricvalues.h"
#include <sstream>

namespace metrics {

template <typename T>
std::string
CountMetricValues<T>::toString() const {
    std::ostringstream ost;
    ost << _value;
    return ost.str();
}
template <typename T>
double
CountMetricValues<T>::getDoubleValue(stringref) const {
    return static_cast<double>(_value);
}
template <typename T>
uint64_t
CountMetricValues<T>::getLongValue(stringref) const {
    return static_cast<uint64_t>(_value);
}
template <typename T>
void
CountMetricValues<T>::output(const std::string&, std::ostream& out) const {
    out << _value;
}
template <typename T>
void
CountMetricValues<T>::output(const std::string&, vespalib::JsonStream& stream) const {
    stream << _value;
}

} // metrics
