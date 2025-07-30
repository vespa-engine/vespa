// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace proton {

/**
 * State explorer for malloc-related information.
 *
 * State emitted:
 *   1. Implementation independent info via `mallinfo()` or `mallinfo2()` (if supported
 *      by the platform).
 *   2. Malloc-implementation specific information for implementations we know about.
 *      Currently only covers vespamalloc and mimalloc.
 */
class MallocInfoExplorer : public vespalib::StateExplorer {
public:
    ~MallocInfoExplorer() override = default;
    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

}
