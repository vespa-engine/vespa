// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

/**
 * This class defines the {@link Trace} levels used by the tracing code.
 */
class TraceLevel {
private:
    TraceLevel(); // hide

public:
    enum {
        // The trace level used for tracing whenever an Error is added to a
        // Reply.
        ERROR = 1,

        // The trace level used by messagebus when sending and receiving
        // messages and replies on network level.
        SEND_RECEIVE = 4,

        // The trace level used by messagebus when splitting messages and
        // merging replies.
        SPLIT_MERGE = 5,

        // The trace level used by messagebus to trace information about which
        // internal components are processing a routable.
        COMPONENT = 6
    };
};

} // namespace vespalib

