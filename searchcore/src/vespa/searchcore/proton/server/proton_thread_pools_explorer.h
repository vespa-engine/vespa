// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/state_explorer.h>

namespace vespalib { class SyncableThreadExecutor; }

namespace proton {

/**
 * Class used to explore the shared thread pools used by proton and it's document databases.
 */
class ProtonThreadPoolsExplorer : public vespalib::StateExplorer {
private:
    const vespalib::SyncableThreadExecutor* _shared;
    const vespalib::SyncableThreadExecutor* _match;
    const vespalib::SyncableThreadExecutor* _docsum;
    const vespalib::SyncableThreadExecutor* _flush;
    const vespalib::SyncableThreadExecutor* _proton;
    const vespalib::SyncableThreadExecutor* _warmup;

public:
    ProtonThreadPoolsExplorer(const vespalib::SyncableThreadExecutor* shared,
                              const vespalib::SyncableThreadExecutor* match,
                              const vespalib::SyncableThreadExecutor* docsum,
                              const vespalib::SyncableThreadExecutor* flush,
                              const vespalib::SyncableThreadExecutor* proton,
                              const vespalib::SyncableThreadExecutor* warmup);

    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}

