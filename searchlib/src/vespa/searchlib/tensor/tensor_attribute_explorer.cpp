// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_attribute_explorer.h"
#include "nearest_neighbor_index.h"
#include "tensor_store.h"
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/util/rcuvector.h>

using vespalib::slime::ObjectInserter;

namespace search::tensor {

namespace {

const std::string NEAREST_NEIGHBOR_INDEX_NAME("nearest_neighbor_index");

}

TensorAttributeExplorer::TensorAttributeExplorer(uint64_t compact_generation,
                                                 const vespalib::RcuVectorBase<vespalib::datastore::AtomicEntryRef>&
                                                 ref_vector,
                                                 const TensorStore& tensor_store,
                                                 const NearestNeighborIndex* index)
    : _compact_generation(compact_generation),
      _ref_vector(ref_vector),
      _tensor_store(tensor_store),
      _index(index)
{
}

TensorAttributeExplorer::~TensorAttributeExplorer() = default;

void
TensorAttributeExplorer::get_state(const vespalib::slime::Inserter& inserter, bool full) const
{
    (void) full;
    auto& object = inserter.insertObject();
    object.setLong("compact_generation", _compact_generation);
    StateExplorerUtils::memory_usage_to_slime(_ref_vector.getMemoryUsage(),
                                              object.setObject("ref_vector").setObject("memory_usage"));
    StateExplorerUtils::memory_usage_to_slime(_tensor_store.getMemoryUsage(),
                                              object.setObject("tensor_store").setObject("memory_usage"));
}

std::vector<std::string>
TensorAttributeExplorer::get_children_names() const
{
    return { NEAREST_NEIGHBOR_INDEX_NAME };
}

std::unique_ptr<vespalib::StateExplorer>
TensorAttributeExplorer::get_child(std::string_view name) const
{
    if (name == NEAREST_NEIGHBOR_INDEX_NAME) {
        if (_index != nullptr) {
            return _index->make_state_explorer();
        }
    }
    return {};
}

}
