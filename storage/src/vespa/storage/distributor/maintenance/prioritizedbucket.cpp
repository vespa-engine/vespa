// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prioritizedbucket.h"
#include <iostream>

namespace storage::distributor {

const PrioritizedBucket PrioritizedBucket::INVALID = PrioritizedBucket();

std::ostream&
operator<<(std::ostream& os, const PrioritizedBucket& bucket)
{
    os << bucket.toString();
    return os;
}

}
