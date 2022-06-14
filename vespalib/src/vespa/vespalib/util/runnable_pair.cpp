// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "runnable_pair.h"

namespace vespalib {

RunnablePair::RunnablePair(Runnable &first, Runnable &second)
    : _first(first),
      _second(second)
{
}

void
RunnablePair::run()
{
    _first.run();
    _second.run();
}

} // namespace vespalib
