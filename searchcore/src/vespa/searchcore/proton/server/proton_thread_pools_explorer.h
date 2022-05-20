// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace vespalib {
class ISequencedTaskExecutor;
class ThreadExecutor;
}

namespace proton {

/**
 * Class used to explore the shared thread pools used by proton and it's document databases.
 */
class ProtonThreadPoolsExplorer : public vespalib::StateExplorer {
private:
    const vespalib::ThreadExecutor* _shared;
    const vespalib::ThreadExecutor* _match;
    const vespalib::ThreadExecutor* _docsum;
    const vespalib::ThreadExecutor* _flush;
    const vespalib::ThreadExecutor* _proton;
    const vespalib::ThreadExecutor* _warmup;
    const vespalib::ISequencedTaskExecutor* _field_writer;

public:
    ProtonThreadPoolsExplorer(const vespalib::ThreadExecutor* shared,
                              const vespalib::ThreadExecutor* match,
                              const vespalib::ThreadExecutor* docsum,
                              const vespalib::ThreadExecutor* flush,
                              const vespalib::ThreadExecutor* proton,
                              const vespalib::ThreadExecutor* warmup,
                              const vespalib::ISequencedTaskExecutor* field_writer);

    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}

