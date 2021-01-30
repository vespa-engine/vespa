// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/state_explorer.h>

namespace proton {

class FlushEngine;

/**
 * Class used to explore the state of a flush engine and its flush targets.
 */
class FlushEngineExplorer : public vespalib::StateExplorer
{
private:
    const FlushEngine &_engine;

public:
    FlushEngineExplorer(const FlushEngine &engine);

    // Implements vespalib::StateExplorer
    virtual void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
};

} // namespace proton

