// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "integerresultnode.h"
#include "integerbucketresultnode.h"

namespace search {
namespace expression {

const BucketResultNode& IntegerResultNode::getNullBucket() const {
    return IntegerBucketResultNode::getNull();
}

}
}

