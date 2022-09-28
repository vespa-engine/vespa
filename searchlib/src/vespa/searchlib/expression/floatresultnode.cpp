// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "floatresultnode.h"
#include "floatbucketresultnode.h"

namespace search {
namespace expression {

const BucketResultNode& FloatResultNode::getNullBucket() const {
    return FloatBucketResultNode::getNull();
}

}
}

