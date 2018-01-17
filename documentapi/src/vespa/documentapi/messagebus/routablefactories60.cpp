// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablefactories60.h"

namespace documentapi {

bool RoutableFactories60::CreateVisitorMessageFactory::encodeBucketSpace(
        vespalib::stringref bucketSpace,
        vespalib::GrowableByteBuffer& buf) const {
    buf.putString(bucketSpace);
    return true;
}

string RoutableFactories60::CreateVisitorMessageFactory::decodeBucketSpace(document::ByteBuffer& buf) const {
    return decodeString(buf);
}

}