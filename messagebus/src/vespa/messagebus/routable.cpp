// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "emptyreply.h"
#include "errorcode.h"
#include "ireplyhandler.h"

namespace mbus {

Routable::Routable() :
    _context(),
    _stack(),
    _trace()
{ }

Routable::~Routable() = default;

void
Routable::discard()
{
    _context = Context();
    _stack.discard();
    _trace.clear();
}

void
Routable::swapState(Routable &rhs)
{
    std::swap(_context, rhs._context);
    _stack.swap(rhs._stack);
    _trace.swap(rhs._trace);
}

} // namespace mbus
