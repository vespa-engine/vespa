// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton::flushengine {

class FlushHistory;

/*
 * State explorer for flush history.
 */
class FlushHistoryExplorer : public vespalib::StateExplorer
{
    std::shared_ptr<FlushHistory> _flush_history;
public:
    FlushHistoryExplorer(std::shared_ptr<FlushHistory> flush_history);
    ~FlushHistoryExplorer() override;

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

}
