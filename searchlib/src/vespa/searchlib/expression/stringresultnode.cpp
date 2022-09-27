// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stringresultnode.h"
#include "stringbucketresultnode.h"

namespace search {
namespace expression {

const BucketResultNode& StringResultNode::getNullBucket() const {
    return StringBucketResultNode::getNull();
}

}
}

