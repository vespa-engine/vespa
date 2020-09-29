// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sparse_tensor_address_ref.h"
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib::tensor {

class SparseTensorIndex : public vespalib::eval::Value::Index
{
public:
    static constexpr size_t STASH_CHUNK_SIZE = 16384u;
    //
    using View = vespalib::eval::Value::Index::View;
    using IndexMap = hash_map<SparseTensorAddressRef, uint32_t, hash<SparseTensorAddressRef>,
                              std::equal_to<>, hashtable_base::and_modulator>;
    // construct
    explicit SparseTensorIndex(size_t num_mapped_dims_in);
    SparseTensorIndex(const SparseTensorIndex & index_in);
    SparseTensorIndex(SparseTensorIndex && index_in) = default;
    ~SparseTensorIndex();
    // Index API
    size_t size() const override;
    std::unique_ptr<View> create_view(const std::vector<size_t> &dims) const override;
    // build API
    void reserve(size_t estimate) { _map.resize(2*estimate); }
    size_t lookup_or_add(SparseTensorAddressRef tmp_ref);
    // lookup API
    bool lookup_address(SparseTensorAddressRef ref, size_t &idx) const;
    // traversal API
    const IndexMap &get_map() const { return _map; }
    // stats
    MemoryUsage get_memory_usage() const;
private:
    Stash _stash;
    IndexMap _map;
    size_t _num_mapped_dims;
    static size_t needed_memory_for(const SparseTensorIndex &other);
};

} // namespace
