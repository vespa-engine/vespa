// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_type.h"
#include <vespa/vespalib/net/http/state_explorer.h>

namespace search::tensor {

template <HnswIndexType type> class HnswIndex;

/**
 * Class used to explore the state of an hnsw index.
 */
template <HnswIndexType type>
class HnswIndexExplorer : public vespalib::StateExplorer
{
    const HnswIndex<type>&  _index;
public:
    HnswIndexExplorer(const HnswIndex<type>& index);
    ~HnswIndexExplorer() override;

    // Implements vespalib::StateExplorer
    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
};

} // namespace proton
