// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "time.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace storage::framework {

std::ostream& operator<<(std::ostream& out, const MicroSecTime & t) {
    return out << t.getTime();
}


vespalib::asciistream& operator<<(vespalib::asciistream& out, const MicroSecTime & t) {
    return out << t.getTime();
}

}
