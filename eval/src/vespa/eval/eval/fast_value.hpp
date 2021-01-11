// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "fast_addr_map.h"
#include "inline_operation.h"
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <vespa/vespalib/util/shared_string_repo.h>

namespace vespalib::eval {

//-----------------------------------------------------------------------------

namespace {

//-----------------------------------------------------------------------------

// look up a full address in the map directly
struct FastLookupView : public Value::Index::View {

    const FastAddrMap &map;
    size_t             subspace;

    FastLookupView(const FastAddrMap &map_in)
        : map(map_in), subspace(FastAddrMap::npos()) {}

    void lookup(ConstArrayRef<const string_id*> addr) override {
        subspace = map.lookup(addr);
    }

    bool next_result(ConstArrayRef<string_id*>, size_t &idx_out) override {
        if (subspace == FastAddrMap::npos()) {
            return false;
        }
        idx_out = subspace;
        subspace = FastAddrMap::npos();
        return true;
    }
};

//-----------------------------------------------------------------------------

// find matching mappings for a partial address with brute force filtering
struct FastFilterView : public Value::Index::View {

    const FastAddrMap        &map;
    std::vector<size_t>       match_dims;
    std::vector<size_t>       extract_dims;
    std::vector<string_id>      query;
    size_t                    pos;

    bool is_match(ConstArrayRef<string_id> addr) const {
        for (size_t i = 0; i < query.size(); ++i) {
            if (query[i] != addr[match_dims[i]]) {
                return false;
            }
        }
        return true;
    }

    FastFilterView(const FastAddrMap &map_in, const std::vector<size_t> &match_dims_in)
        : map(map_in), match_dims(match_dims_in),
          extract_dims(), query(match_dims.size()), pos(FastAddrMap::npos())
    {
        auto my_pos = match_dims.begin();
        for (size_t i = 0; i < map.addr_size(); ++i) {
            if ((my_pos == match_dims.end()) || (*my_pos != i)) {
                extract_dims.push_back(i);
            } else {
                ++my_pos;
            }
        }
        assert(my_pos == match_dims.end());
        assert((match_dims.size() + extract_dims.size()) == map.addr_size());
    }

    void lookup(ConstArrayRef<const string_id*> addr) override {
        assert(addr.size() == query.size());
        for (size_t i = 0; i < addr.size(); ++i) {
            query[i] = *addr[i];
        }
        pos = 0;
    }

    bool next_result(ConstArrayRef<string_id*> addr_out, size_t &idx_out) override {
        while (pos < map.size()) {
            auto addr = map.get_addr(pos);            
            if (is_match(addr)) {
                assert(addr_out.size() == extract_dims.size());
                for (size_t i = 0; i < extract_dims.size(); ++i) {
                    *addr_out[i] = addr[extract_dims[i]];
                }
                idx_out = pos++;
                return true;
            }
            ++pos;
        }
        return false;
    }
};

//-----------------------------------------------------------------------------

// iterate all mappings
struct FastIterateView : public Value::Index::View {

    const FastAddrMap &map;
    size_t             pos;

    FastIterateView(const FastAddrMap &map_in)
        : map(map_in), pos(FastAddrMap::npos()) {}

    void lookup(ConstArrayRef<const string_id*>) override {
        pos = 0;
    }

    bool next_result(ConstArrayRef<string_id*> addr_out, size_t &idx_out) override {
        if (pos >= map.size()) {
            return false;
        }
        auto addr = map.get_addr(pos);
        assert(addr.size() == addr_out.size());
        for (size_t i = 0; i < addr.size(); ++i) {
            *addr_out[i] = addr[i];
        }
        idx_out = pos++;
        return true;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

using JoinAddrSource = instruction::SparseJoinPlan::Source;
// This is the class instructions will look for when optimizing sparse
// operations by calling inline functions directly.

struct FastValueIndex final : Value::Index {
    FastAddrMap map;
    FastValueIndex(size_t num_mapped_dims_in, const std::vector<string_id> &labels, size_t expected_subspaces_in)
        : map(num_mapped_dims_in, labels, expected_subspaces_in) {}

    template <typename LCT, typename RCT, typename OCT, typename Fun>
        static const Value &sparse_full_overlap_join(const ValueType &res_type, const Fun &fun,
                const FastValueIndex &lhs, const FastValueIndex &rhs,
                ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells, Stash &stash);

    template <typename LCT, typename RCT, typename OCT, typename Fun>
        static const Value &sparse_no_overlap_join(const ValueType &res_type, const Fun &fun,
                const FastValueIndex &lhs, const FastValueIndex &rhs,
                const std::vector<JoinAddrSource> &addr_sources,
                ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells, Stash &stash);

    template <typename LCT, typename RCT, typename OCT, typename Fun>
        static const Value &sparse_only_merge(const ValueType &res_type, const Fun &fun,
                const FastValueIndex &lhs, const FastValueIndex &rhs,
                ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells,
                Stash &stash) __attribute((noinline));

    size_t size() const override { return map.size(); }
    std::unique_ptr<View> create_view(const std::vector<size_t> &dims) const override;
};

//-----------------------------------------------------------------------------

template <typename T>
struct FastCells {
    static constexpr size_t elem_size = sizeof(T);
    size_t capacity;
    size_t size;
    void *memory;
    FastCells(size_t initial_capacity)
        : capacity(roundUp2inN(initial_capacity)),
          size(0),
          memory(malloc(elem_size * capacity))
    {
        static_assert(std::is_trivially_copyable_v<T>);
        static_assert(can_skip_destruction<T>::value);
    }
    ~FastCells() {
        free(memory);
    }
    void ensure_free(size_t need) {
        if (__builtin_expect((size + need) > capacity, false)) {
            capacity = roundUp2inN(size + need);
            void *new_memory = malloc(elem_size * capacity);
            memcpy(new_memory, memory, elem_size * size);
            free(memory);
            memory = new_memory;
        }
    }
    constexpr T *get(size_t offset) const {
        return reinterpret_cast<T*>(memory) + offset;
    }
    void push_back_fast(T value) {
        *get(size++) = value;
    }
    ArrayRef<T> add_cells(size_t n) {
        size_t old_size = size;
        ensure_free(n);
        size += n;
        return ArrayRef<T>(get(old_size), n);
    }
    MemoryUsage estimate_extra_memory_usage() const {
        MemoryUsage usage;
        usage.incAllocatedBytes(elem_size * capacity);
        usage.incUsedBytes(elem_size * size);
        return usage;
    }
};

//-----------------------------------------------------------------------------

template <typename T, bool transient>
struct FastValue final : Value, ValueBuilder<T> {

    using Handles = typename std::conditional<transient,
                                     std::vector<string_id>,
                                     SharedStringRepo::Handles>::type;

    static const std::vector<string_id> &get_view(const std::vector<string_id> &handles) { return handles; }
    static const std::vector<string_id> &get_view(const SharedStringRepo::Handles &handles) { return handles.view(); }

    ValueType my_type;
    size_t my_subspace_size;
    Handles my_handles;
    FastValueIndex my_index;
    FastCells<T> my_cells;

    FastValue(const ValueType &type_in, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in)
        : my_type(type_in), my_subspace_size(subspace_size_in),
          my_handles(),
          my_index(num_mapped_dims_in, get_view(my_handles), expected_subspaces_in),
          my_cells(subspace_size_in * expected_subspaces_in)
    {
        my_handles.reserve(expected_subspaces_in * num_mapped_dims_in);
    }
    ~FastValue() override;
    const ValueType &type() const override { return my_type; }
    const Value::Index &index() const override { return my_index; }
    TypedCells cells() const override { return TypedCells(my_cells.memory, get_cell_type<T>(), my_cells.size); }
    void add_mapping(ConstArrayRef<vespalib::stringref> addr) {
        if constexpr (transient) {
            (void) addr;
            abort(); // cannot use this for transient values
        } else {
            uint32_t hash = 0;
            for (const auto &label: addr) {
                hash = FastAddrMap::combine_label_hash(hash, FastAddrMap::hash_label(my_handles.add(label)));
            }
            my_index.map.add_mapping(hash);
        }
    }
    void add_mapping(ConstArrayRef<string_id> addr) {
        uint32_t hash = 0;
        for (string_id label: addr) {
            hash = FastAddrMap::combine_label_hash(hash, FastAddrMap::hash_label(label));
            my_handles.push_back(label);
        }
        my_index.map.add_mapping(hash);
    }
    void add_mapping(ConstArrayRef<string_id> addr, uint32_t hash) {
        for (string_id label: addr) {
            my_handles.push_back(label);
        }
        my_index.map.add_mapping(hash);
    }
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override {
        add_mapping(addr);
        return my_cells.add_cells(my_subspace_size);
    }
    ArrayRef<T> add_subspace(ConstArrayRef<string_id> addr) override {
        add_mapping(addr);
        return my_cells.add_cells(my_subspace_size);        
    }
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        if (my_index.map.addr_size() == 0) {
            assert(my_index.map.size() == 1);
        }
        assert(my_cells.size == (my_index.map.size() * my_subspace_size));
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<FastValue<T,transient>>();
        usage.merge(vector_extra_memory_usage(get_view(my_handles)));
        usage.merge(my_index.map.estimate_extra_memory_usage());
        usage.merge(my_cells.estimate_extra_memory_usage());
        return usage;
    }
};
template <typename T,bool transient> FastValue<T,transient>::~FastValue() = default;

//-----------------------------------------------------------------------------

template <typename T>
struct FastDenseValue final : Value, ValueBuilder<T> {

    ValueType my_type;
    FastCells<T> my_cells;

    FastDenseValue(const ValueType &type_in, size_t subspace_size_in)
        : my_type(type_in), my_cells(subspace_size_in)
    {
        my_cells.add_cells(subspace_size_in);
    }
    ~FastDenseValue() override;
    const ValueType &type() const override { return my_type; }
    const Value::Index &index() const override { return TrivialIndex::get(); }
    TypedCells cells() const override { return TypedCells(my_cells.memory, get_cell_type<T>(), my_cells.size); }
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref>) override {
        return ArrayRef<T>(my_cells.get(0), my_cells.size);
    }
    ArrayRef<T> add_subspace(ConstArrayRef<string_id>) override {
        return ArrayRef<T>(my_cells.get(0), my_cells.size);
    }
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<FastDenseValue<T>>();
        usage.merge(my_cells.estimate_extra_memory_usage());
        return usage;
    }
};
template <typename T> FastDenseValue<T>::~FastDenseValue() = default;

//-----------------------------------------------------------------------------

template <typename T>
struct FastScalarBuilder final : ValueBuilder<T> {
    T _value;
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref>) final override { return ArrayRef<T>(&_value, 1); }
    ArrayRef<T> add_subspace(ConstArrayRef<string_id>) final override { return ArrayRef<T>(&_value, 1); };
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>>) final override { return std::make_unique<ScalarValue<T>>(_value); }
};

//-----------------------------------------------------------------------------

template <typename LCT, typename RCT, typename OCT, typename Fun>
const Value &
FastValueIndex::sparse_full_overlap_join(const ValueType &res_type, const Fun &fun,
                                         const FastValueIndex &lhs, const FastValueIndex &rhs,
                                         ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells, Stash &stash)
{
    auto &result = stash.create<FastValue<OCT,true>>(res_type, lhs.map.addr_size(), 1, lhs.map.size());
    lhs.map.each_map_entry([&](auto lhs_subspace, auto hash) {
                auto lhs_addr = lhs.map.get_addr(lhs_subspace);
                auto rhs_subspace = rhs.map.lookup(lhs_addr, hash);
                if (rhs_subspace != FastAddrMap::npos()) {
                    result.add_mapping(lhs_addr, hash);
                    auto cell_value = fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]);
                    result.my_cells.push_back_fast(cell_value);
                }
            });
    return result;
}

//-----------------------------------------------------------------------------

template <typename LCT, typename RCT, typename OCT, typename Fun>
const Value &
FastValueIndex::sparse_no_overlap_join(const ValueType &res_type, const Fun &fun,
                                       const FastValueIndex &lhs, const FastValueIndex &rhs,
                                       const std::vector<JoinAddrSource> &addr_sources,
                                       ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells, Stash &stash)
{
    size_t num_mapped_dims = addr_sources.size();
    auto &result = stash.create<FastValue<OCT,true>>(res_type, num_mapped_dims, 1, lhs.map.size()*rhs.map.size());
    std::vector<string_id> output_addr(num_mapped_dims);
    std::vector<size_t> store_lhs_idx;
    std::vector<size_t> store_rhs_idx;
    size_t out_idx = 0;
    for (JoinAddrSource source : addr_sources) {
        switch (source) {
        case JoinAddrSource::LHS:
            store_lhs_idx.push_back(out_idx++);
            break;
        case JoinAddrSource::RHS:
            store_rhs_idx.push_back(out_idx++);
            break;
        default:
            abort();
        }
    }
    assert(out_idx == output_addr.size());
    for (size_t lhs_subspace = 0; lhs_subspace < lhs.map.size(); ++lhs_subspace) {
        auto l_addr = lhs.map.get_addr(lhs_subspace);
        assert(l_addr.size() == store_lhs_idx.size());
        for (size_t i = 0; i < store_lhs_idx.size(); ++i) {
            size_t addr_idx = store_lhs_idx[i];
            output_addr[addr_idx] = l_addr[i];
        }
        for (size_t rhs_subspace = 0; rhs_subspace < rhs.map.size(); ++rhs_subspace) {
            auto r_addr = rhs.map.get_addr(rhs_subspace);
            assert(r_addr.size() == store_rhs_idx.size());
            for (size_t i = 0; i < store_rhs_idx.size(); ++i) {
                size_t addr_idx = store_rhs_idx[i];
                output_addr[addr_idx] = r_addr[i];
            }
            result.add_mapping(ConstArrayRef(output_addr));
            auto cell_value = fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]);
            result.my_cells.push_back_fast(cell_value);
        }
    }
    return result;
}

//-----------------------------------------------------------------------------

template <typename LCT, typename RCT, typename OCT, typename Fun>
const Value &
FastValueIndex::sparse_only_merge(const ValueType &res_type, const Fun &fun,
                             const FastValueIndex &lhs, const FastValueIndex &rhs,
                             ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells, Stash &stash)
{
    size_t guess_size = lhs.map.size() + rhs.map.size();
    auto &result = stash.create<FastValue<OCT,true>>(res_type, lhs.map.addr_size(), 1, guess_size);
    lhs.map.each_map_entry([&](auto lhs_subspace, auto hash)
                           {
                               result.add_mapping(lhs.map.get_addr(lhs_subspace), hash);
                               result.my_cells.push_back_fast(lhs_cells[lhs_subspace]);
                           });
    rhs.map.each_map_entry([&](auto rhs_subspace, auto hash)
                           {
                               auto rhs_addr = rhs.map.get_addr(rhs_subspace);
                               auto result_subspace = result.my_index.map.lookup(rhs_addr, hash);
                               if (result_subspace == FastAddrMap::npos()) {
                                   result.add_mapping(rhs_addr, hash);
                                   result.my_cells.push_back_fast(rhs_cells[rhs_subspace]);
                               } else {
                                   OCT &out_cell = *result.my_cells.get(result_subspace);
                                   out_cell = fun(out_cell, rhs_cells[rhs_subspace]);
                               }
                           });
    return result;
}

}
