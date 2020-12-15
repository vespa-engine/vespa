// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/state_explorer.h>

namespace proton {

class ExecutorThreadingService;

/**
 * Class used to explore the state of the ExecutorThreadingService used in a document database.
 */
class ExecutorThreadingServiceExplorer : public vespalib::StateExplorer {
private:
    ExecutorThreadingService& _service;

public:
    ExecutorThreadingServiceExplorer(ExecutorThreadingService& service);
    ~ExecutorThreadingServiceExplorer();

    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}
