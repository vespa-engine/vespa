// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operationtargetresolver.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {
namespace distributor {

document::Bucket
OperationTarget::getBucket() const
{
    return document::Bucket(document::BucketSpace::placeHolder(), _bucket);
}

void
OperationTarget::print(vespalib::asciistream& out, const PrintProperties&) const {
    out << "OperationTarget(" << _bucket << ", " << _node
        << (_newCopy ? ", new copy" : ", existing copy") << ")";
}

}
}

