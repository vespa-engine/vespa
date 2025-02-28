// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/state_explorer.h>

namespace vespalib::datastore { class AtomicEntryRef; }
namespace vespalib { template <typename T> class RcuVectorBase; }

namespace search::tensor {

class NearestNeighborIndex;
class TensorStore;

/**
 * Class used to explore the state of a tensor attribute vector.
 */
class TensorAttributeExplorer : public vespalib::StateExplorer
{
    uint64_t                                                            _compact_generation;
    const vespalib::RcuVectorBase<vespalib::datastore::AtomicEntryRef>& _ref_vector;
    const TensorStore&                                                  _tensor_store;
    const NearestNeighborIndex*                                         _index;
public:
    TensorAttributeExplorer(uint64_t compact_generation,
                            const vespalib::RcuVectorBase<vespalib::datastore::AtomicEntryRef>& ref_vector,
                            const TensorStore& tensor_store,
                            const NearestNeighborIndex* index);
    ~TensorAttributeExplorer() override;

    // Implements vespalib::StateExplorer
    void get_state(const vespalib::slime::Inserter& inserter, bool full) const override;
    std::vector<std::string> get_children_names() const override;
    std::unique_ptr<StateExplorer> get_child(std::string_view name) const override;
};

} // namespace proton

