// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "trace.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/fastos/timestamp.h>

namespace search::engine {

Trace::Trace(const fastos::TimeStamp &start_time)
    : _trace(std::make_unique<vespalib::Slime>()),
      _root(_trace->setObject()),
      _traces(_root.setArray("traces"))
{
   _root.setLong("creation_time", start_time);
}

Trace::~Trace() = default;

Trace::Cursor &
Trace::createCursor(vespalib::stringref name) {
    Cursor & trace = _traces.addObject();
    trace.setString("tag", name);
    return trace;
}

vespalib::string
Trace::toString() const {
    return _trace->toString();
}

}
