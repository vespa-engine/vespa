// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace searchcorespi::index { struct IThreadingService; }
namespace proton {

class ExecutorThreadingService;

/**
 * Class used to explore the state of the ExecutorThreadingService used in a document database.
 */
class ExecutorThreadingServiceExplorer : public vespalib::StateExplorer {
private:
    searchcorespi::index::IThreadingService& _service;

public:
    ExecutorThreadingServiceExplorer(searchcorespi::index::IThreadingService& service);
    ~ExecutorThreadingServiceExplorer();

    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}
