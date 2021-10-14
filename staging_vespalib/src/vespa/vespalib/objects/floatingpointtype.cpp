// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/objects/floatingpointtype.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace vespalib {

template<typename Number>
std::ostream& operator<<(std::ostream& out, FloatingPointType<Number> number)
{
    return out << number.getValue();
}

template<typename Number>
vespalib::asciistream & operator<<(vespalib::asciistream & out, FloatingPointType<Number> number)
{
    return out << number.getValue();
}

template std::ostream& operator<<(std::ostream& out, FloatingPointType<float> number);
template std::ostream& operator<<(std::ostream& out, FloatingPointType<double> number);

template vespalib::asciistream& operator<<(vespalib::asciistream& out, FloatingPointType<float> number);
template vespalib::asciistream& operator<<(vespalib::asciistream& out, FloatingPointType<double> number);

}
