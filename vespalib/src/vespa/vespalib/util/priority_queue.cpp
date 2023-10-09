// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "priority_queue.h"

namespace vespalib {

template class PriorityQueue<int>;
template class PriorityQueue<int, std::greater<int> >;

} // namespace vespalib
