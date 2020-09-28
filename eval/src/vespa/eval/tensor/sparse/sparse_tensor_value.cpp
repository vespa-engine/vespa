// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_value.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_address_decoder.h"

#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.sparse.sparse_tensor_value");

namespace vespalib::tensor {

using SubspaceMap = SparseTensorValueIndex::SubspaceMap;
using View = vespalib::eval::Value::Index::View;

namespace {

void copyMap(SubspaceMap &map, const SubspaceMap &map_in, Stash &stash) {
    // copy the exact hashtable structure:
    map = map_in;
    // copy the actual contents of the addresses,
    // and update the pointers inside the hashtable
    // keys so they point to our copy:
    for (auto & kv : map) {
        SparseTensorAddressRef oldRef = kv.first;
        SparseTensorAddressRef newRef(oldRef, stash);
        kv.first = newRef;
    }
}

template<typename T>
size_t needed_memory_for(const SubspaceMap &map, ConstArrayRef<T> cells) {
    size_t needs = cells.size() * sizeof(T);
    for (const auto & kv : map) {
        needs += kv.first.size();
    }
    return needs;
}

//-----------------------------------------------------------------------------

class SparseTensorValueView : public View
{
private:
    const SubspaceMap &map;
    SubspaceMap::const_iterator iter;
    const std::vector<size_t> lookup_dims;
    std::vector<vespalib::stringref> lookup_refs;
public:
    SparseTensorValueView(const SubspaceMap & map_in,
                          const std::vector<size_t> &dims)
      : map(map_in), iter(map.end()), lookup_dims(dims), lookup_refs() {}
    ~SparseTensorValueView();
    void lookup(const std::vector<const vespalib::stringref*> &addr) override;
    bool next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out) override;
};

SparseTensorValueView::~SparseTensorValueView() = default;

void
SparseTensorValueView::lookup(const std::vector<const vespalib::stringref*> &addr)
{
    lookup_refs.clear();
    for (auto ptr : addr) {
        lookup_refs.push_back(*ptr);
    }
    iter = map.begin();
    
}

bool
SparseTensorValueView::next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out)
{
    size_t total_dims = lookup_refs.size() + addr_out.size();
    while (iter != map.end()) {
        const auto & ref = iter->first;
        SparseTensorAddressDecoder decoder(ref);
        idx_out = iter->second;
        ++iter;
        bool couldmatch = true;
        size_t vd_idx = 0;
        size_t ao_idx = 0;
        for (size_t i = 0; i < total_dims; ++i) {
            const auto label = decoder.decodeLabel();
            if (vd_idx < lookup_dims.size()) {
                size_t next_view_dim = lookup_dims[vd_idx];
                if (i == next_view_dim) {
                    if (label == lookup_refs[vd_idx]) {
                        // match in this dimension
                        ++vd_idx;
                        continue;
                    } else {
                        // does not match
                        couldmatch = false;
                        break;
                    }
                }
            }
            // not a view dimension:
            *addr_out[ao_idx] = label;
            ++ao_idx;
        }
        if (couldmatch) {
            assert(vd_idx == lookup_dims.size());
            assert(ao_idx == addr_out.size());
            return true;
        }
    }
    return false;
}

//-----------------------------------------------------------------------------

class SparseTensorValueLookup : public View
{
private:
    const SubspaceMap &map;
    SubspaceMap::const_iterator iter;
public:
    SparseTensorValueLookup(const SubspaceMap & map_in) : map(map_in), iter(map.end()) {}
    ~SparseTensorValueLookup();
    void lookup(const std::vector<const vespalib::stringref*> &addr) override;
    bool next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out) override;
};

SparseTensorValueLookup::~SparseTensorValueLookup() = default;

void
SparseTensorValueLookup::lookup(const std::vector<const vespalib::stringref*> &addr)
{
    SparseTensorAddressBuilder builder;
    for (const auto & label : addr) {
        builder.add(*label);
    }
    auto ref = builder.getAddressRef();
    iter = map.find(ref);
}

bool
SparseTensorValueLookup::next_result(const std::vector<vespalib::stringref*> &, size_t &idx_out)
{
    if (iter != map.end()) {
        idx_out = iter->second;
        iter = map.end();
        return true;
    }
    return false;
}

//-----------------------------------------------------------------------------

class SparseTensorValueAllMappings : public View
{
private:
    const SubspaceMap &map;
    SubspaceMap::const_iterator iter;
public:
    SparseTensorValueAllMappings(const SubspaceMap & map_in) : map(map_in), iter(map.end()) {}
    ~SparseTensorValueAllMappings();
    void lookup(const std::vector<const vespalib::stringref*> &addr) override;
    bool next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out) override;
};

SparseTensorValueAllMappings::~SparseTensorValueAllMappings() = default;

void
SparseTensorValueAllMappings::lookup(const std::vector<const vespalib::stringref*> &)
{
    iter = map.begin();
}

bool
SparseTensorValueAllMappings::next_result(const std::vector<vespalib::stringref*> &addr_out, size_t &idx_out)
{
    if (iter != map.end()) {
        const auto & ref = iter->first;
        idx_out = iter->second;
        ++iter;
        SparseTensorAddressDecoder decoder(ref);
        for (const auto ptr : addr_out) {
            const auto label = decoder.decodeLabel();
            *ptr = label;
        }
        return true;
    }
    return false;
}

} // namespace <unnamed>

//-----------------------------------------------------------------------------

SparseTensorValueIndex::SparseTensorValueIndex(size_t num_mapped_in)
    : map(), num_mapped_dims(num_mapped_in) {}

SparseTensorValueIndex::~SparseTensorValueIndex() = default;

size_t SparseTensorValueIndex::size() const {
    return map.size();
}

std::unique_ptr<View>
SparseTensorValueIndex::create_view(const std::vector<size_t> &dims) const
{
    if (dims.size() == num_mapped_dims) {
        return std::make_unique<SparseTensorValueLookup>(map);
    }
    if (dims.size() == 0) {
        return std::make_unique<SparseTensorValueAllMappings>(map);
    }
    return std::make_unique<SparseTensorValueView>(map, dims);
}

//-----------------------------------------------------------------------------

template<typename T>
SparseTensorValue::SparseTensorValue(const eval::ValueType &type_in, const SparseTensorValueIndex &index_in, ConstArrayRef<T> cells_in)
    : _type(type_in),
      _index(index_in.num_mapped_dims),
      _cells(),
      _stash(needed_memory_for(index_in.map, cells_in))
{
    copyMap(_index.map, index_in.map, _stash);
    _cells = TypedCells(_stash.copy_array<T>(cells_in));
}

SparseTensorValue::SparseTensorValue(eval::ValueType &&type_in, SparseTensorValueIndex &&index_in, TypedCells cells_in, Stash &&stash_in)
    : _type(std::move(type_in)),
      _index(std::move(index_in)),
      _cells(cells_in),
      _stash(std::move(stash_in))
{
}

SparseTensorValue::~SparseTensorValue() = default;

//-----------------------------------------------------------------------------

} // namespace

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::tensor::SparseTensorAddressRef, uint32_t);
