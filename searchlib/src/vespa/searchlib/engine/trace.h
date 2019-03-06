// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace fastos { class TimeStamp; }
namespace vespalib { class Slime; }
namespace vespalib::slime { class Cursor; }

namespace search::engine {

    /**
     * Used for adding traces to a request. Acquire a new Cursor for everytime you want to trace something.
     * Note that it is not thread safe. All use of any cursor aquired must be thread safe.
     */
class Trace
{
public:
    using Cursor = vespalib::slime::Cursor;
    Trace(const fastos::TimeStamp &start_time);
    ~Trace();

    /**
     * Will give you a trace entry. It will also add a timestamp relative to the creation of the trace.
     * @param name
     * @return a Cursor to use for further tracing.
     */
    Cursor & createCursor(vespalib::stringref name);
    vespalib::string toString() const;
    vespalib::Slime & getRoot() const { return *_trace; }
private:
    std::unique_ptr<vespalib::Slime> _trace;
    Cursor & _root;
    Cursor & _traces;
};

}
