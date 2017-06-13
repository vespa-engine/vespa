// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class LoadType
 * \ingroup loadtype
 *
 * \brief Class used to identify a given load type.
 *
 * A load type is a type given to a Vespa operation that is independent of the
 * message type, priority or such information. Load types are given by clients
 * to external load (if not given, default load type is used), and might also be
 * set by the system itself for maintenance load.
 */

#pragma once

#include <vespa/metrics/loadtype.h>
#include <vespa/documentapi/messagebus/priority.h>

namespace vespalib {
    class asciistream;
}

namespace documentapi {

// Inherit metrics loadtype so it is easy to use load types in load metrics.
class LoadType : public metrics::LoadType {
    Priority::Value _priority;

public:
    using UP = std::unique_ptr<LoadType>;

    LoadType(uint32_t id, const string& name, Priority::Value priority)
        : metrics::LoadType(id, name), _priority(priority) {}
    static const LoadType DEFAULT;

    Priority::Value getPriority() const { return _priority; }
private:
    void print(vespalib::asciistream & os) const;
};

}
