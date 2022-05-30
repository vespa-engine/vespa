// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "time.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {
namespace framework {

template<typename Type, int MPU>
std::ostream& operator<<(std::ostream& out, const Time<Type, MPU>& t) {
    return out << t.getTime();
}

template<typename Type, int MPU>
vespalib::asciistream& operator<<(vespalib::asciistream& out, const Time<Type, MPU>& t) {
    return out << t.getTime();
}

} // framework
} // storage
