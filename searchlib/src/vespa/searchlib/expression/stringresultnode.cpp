// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "stringbucketresultnode.h"
#include "stringresultnode.h"

namespace search {
namespace expression {

const BucketResultNode& StringResultNode::getNullBucket() const {
    return StringBucketResultNode::getNull();
}

}
}

