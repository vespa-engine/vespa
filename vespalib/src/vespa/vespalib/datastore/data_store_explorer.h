// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace vespalib::datastore {

class DataStoreBase;

class DataStoreExplorer : public StateExplorer {
    const DataStoreBase& _store;

public:
    DataStoreExplorer(const DataStoreBase& store);
    ~DataStoreExplorer() override;

    // Implements vespalib::StateExplorer
    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}
