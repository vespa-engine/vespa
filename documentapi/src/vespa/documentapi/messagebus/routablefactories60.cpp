// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routablefactories60.h"

namespace documentapi {

// TODO dedupe

bool RoutableFactories60::CreateVisitorMessageFactory::encodeBucketSpace(
        vespalib::stringref bucketSpace,
        vespalib::GrowableByteBuffer& buf) const {
    doEncodeBucketSpace(bucketSpace, buf);
    return true;
}

string RoutableFactories60::CreateVisitorMessageFactory::decodeBucketSpace(document::ByteBuffer& buf) const {
    return doDecodeBucketSpace(buf);
}

bool RoutableFactories60::StatBucketMessageFactory::encodeBucketSpace(
        vespalib::stringref bucketSpace,
        vespalib::GrowableByteBuffer& buf) const {
    doEncodeBucketSpace(bucketSpace, buf);
    return true;
}

string RoutableFactories60::StatBucketMessageFactory::decodeBucketSpace(document::ByteBuffer& buf) const {
    return doDecodeBucketSpace(buf);
}

bool RoutableFactories60::GetBucketListMessageFactory::encodeBucketSpace(
        vespalib::stringref bucketSpace,
        vespalib::GrowableByteBuffer& buf) const {
    doEncodeBucketSpace(bucketSpace, buf);
    return true;
}

string RoutableFactories60::GetBucketListMessageFactory::decodeBucketSpace(document::ByteBuffer& buf) const {
    return doDecodeBucketSpace(buf);
}

void RoutableFactories60::doEncodeBucketSpace(
        vespalib::stringref bucketSpace,
        vespalib::GrowableByteBuffer& buf) {
    buf.putString(bucketSpace);
}
string RoutableFactories60::doDecodeBucketSpace(document::ByteBuffer& buf) {
    return decodeString(buf);
}

}