// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationtargetresolver.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage::distributor {

void
OperationTarget::print(vespalib::asciistream& out, const PrintProperties&) const {
    out << "OperationTarget(" << _bucket.toString() << ", " << _node
        << (_newCopy ? ", new copy" : ", existing copy") << ")";
}

}
