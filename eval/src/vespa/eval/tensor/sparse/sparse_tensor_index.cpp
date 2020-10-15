// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_tensor_index.h"
#include "sparse_tensor_address_builder.h"
#include "sparse_tensor_address_decoder.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_map_equal.hpp>

namespace vespalib::tensor {

using IndexMap = SparseTensorIndex::IndexMap;
using View = vespalib::eval::Value::Index::View;

namespace {

void copyMap(IndexMap &map, const IndexMap &map_in, Stash &to_stash) {
    // copy the exact hashtable structure:
    map = map_in;
    // copy the actual contents of the addresses,
    // and update the pointers inside the hashtable
    // keys so they point to our copy:
    for (auto & kv : map) {
        SparseTensorAddressRef oldRef = kv.first;
        SparseTensorAddressRef newRef(oldRef, to_stash);
        kv.first = newRef;
    }
}

//-----------------------------------------------------------------------------

class SparseTensorValueView : public View
{
private:
    const IndexMap &map;
    IndexMap::const_iterator iter;
    const std::vector<size_t> lookup_dims;
    std::vector<vespalib::stringref> lookup_refs;
public:
    SparseTensorValueView(const IndexMap & map_in,
                          const std::vector<size_t> &dims)
      : map(map_in), iter(map.end()), lookup_dims(dims), lookup_refs() {}
    ~SparseTensorValueView();
    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override;
    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override;
};

SparseTensorValueView::~SparseTensorValueView() = default;

void
SparseTensorValueView::lookup(ConstArrayRef<const vespalib::stringref*> addr)
{
    lookup_refs.clear();
    for (auto ptr : addr) {
        lookup_refs.push_back(*ptr);
    }
    iter = map.begin();
    
}

bool
SparseTensorValueView::next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out)
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
    const IndexMap &map;
    IndexMap::const_iterator iter;
public:
    SparseTensorValueLookup(const IndexMap & map_in) : map(map_in), iter(map.end()) {}
    ~SparseTensorValueLookup();
    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override;
    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override;
};

SparseTensorValueLookup::~SparseTensorValueLookup() = default;

void
SparseTensorValueLookup::lookup(ConstArrayRef<const vespalib::stringref*> addr)
{
    SparseTensorAddressBuilder builder;
    for (const auto & label : addr) {
        builder.add(*label);
    }
    auto ref = builder.getAddressRef();
    iter = map.find(ref);
}

bool
SparseTensorValueLookup::next_result(ConstArrayRef<vespalib::stringref*>, size_t &idx_out)
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
    const IndexMap &map;
    IndexMap::const_iterator iter;
public:
    SparseTensorValueAllMappings(const IndexMap & map_in) : map(map_in), iter(map.end()) {}
    ~SparseTensorValueAllMappings();
    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override;
    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override;
};

SparseTensorValueAllMappings::~SparseTensorValueAllMappings() = default;

void
SparseTensorValueAllMappings::lookup(ConstArrayRef<const vespalib::stringref*>)
{
    iter = map.begin();
}

bool
SparseTensorValueAllMappings::next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out)
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

size_t
SparseTensorIndex::needed_memory_for(const SparseTensorIndex &other) {
    auto mem = other._stash.get_memory_usage();
    size_t mem_use = mem.usedBytes();
    if (mem_use == 0) {
        return STASH_CHUNK_SIZE;
    }
    if (mem_use < (STASH_CHUNK_SIZE / 4)) {
        size_t avg_per_addr = mem_use / other.size();
        mem_use = std::max(mem_use, (7 * avg_per_addr));
        size_t aligned_size = (mem_use + 63) & ~(sizeof(char *) - 1);
        return aligned_size;
    }
    return STASH_CHUNK_SIZE;
}

SparseTensorIndex::SparseTensorIndex(size_t num_mapped_in)
    : _stash(STASH_CHUNK_SIZE), _map(), _num_mapped_dims(num_mapped_in)
{}

SparseTensorIndex::SparseTensorIndex(size_t stash_size, const SparseTensorIndex &index_in)
    : _stash(stash_size), _map(), _num_mapped_dims(index_in._num_mapped_dims)
{
    copyMap(_map, index_in._map, _stash);    
}

SparseTensorIndex
SparseTensorIndex::shrunk_copy() const
{
    size_t want_mem = needed_memory_for(*this);
    return SparseTensorIndex(want_mem, *this);
}

SparseTensorIndex
SparseTensorIndex::copy() const
{
    size_t want_mem = _stash.get_chunk_size();
    return SparseTensorIndex(want_mem, *this);
}

void
SparseTensorIndex::reserve(size_t estimate) {
    _map.resize(2*estimate);
}

SparseTensorIndex::~SparseTensorIndex() = default;

size_t SparseTensorIndex::size() const {
    return _map.size();
}

std::unique_ptr<View>
SparseTensorIndex::create_view(const std::vector<size_t> &dims) const
{
    if (dims.size() == _num_mapped_dims) {
        return std::make_unique<SparseTensorValueLookup>(_map);
    }
    if (dims.size() == 0) {
        return std::make_unique<SparseTensorValueAllMappings>(_map);
    }
    return std::make_unique<SparseTensorValueView>(_map, dims);
}

void
SparseTensorIndex::add_address(SparseTensorAddressRef tmp_ref)
{
    SparseTensorAddressRef ref(tmp_ref, _stash);
    size_t idx = _map.size();
    auto insert_result = _map.insert({ref, idx});
    assert(insert_result.second);
}
    
size_t
SparseTensorIndex::lookup_or_add(SparseTensorAddressRef tmp_ref)
{
    auto [map_iter, was_inserted] = _map.insert({tmp_ref, _map.size()});
    if (was_inserted) {
        // we must copy the memory tmp_ref refers to into our own stash:
        SparseTensorAddressRef ref(tmp_ref, _stash);
        // and update the key in the map, just like copyMap() does.
        map_iter->first = ref;
    }
    return map_iter->second;
}

bool
SparseTensorIndex::lookup_address(SparseTensorAddressRef ref, size_t &idx) const
{
    auto iter = _map.find(ref);
    if (iter != _map.end()) {
        idx = iter->second;
        return true;
    }
    idx = size_t(-1);
    return false;
}

MemoryUsage
SparseTensorIndex::get_memory_usage() const
{
    MemoryUsage mem = _stash.get_memory_usage();
    size_t plus = _map.getMemoryConsumption();
    mem.incUsedBytes(plus);
    mem.incAllocatedBytes(plus);
    return mem;
}


//-----------------------------------------------------------------------------

} // namespace

VESPALIB_HASH_MAP_INSTANTIATE(vespalib::tensor::SparseTensorAddressRef, uint32_t);
