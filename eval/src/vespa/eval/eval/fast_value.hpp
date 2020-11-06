// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "fast_sparse_map.h"
#include "inline_operation.h"
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/alloc.h>

namespace vespalib::eval {

//-----------------------------------------------------------------------------

namespace {

//-----------------------------------------------------------------------------

// look up a full address in the map directly
struct LookupView : public Value::Index::View {

    const FastSparseMap &map;
    size_t               subspace;

    LookupView(const FastSparseMap &map_in)
        : map(map_in), subspace(FastSparseMap::npos()) {}

    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override {
        subspace = map.lookup(addr);
    }

    bool next_result(ConstArrayRef<vespalib::stringref*>, size_t &idx_out) override {
        if (subspace == FastSparseMap::npos()) {
            return false;
        }
        idx_out = subspace;
        subspace = FastSparseMap::npos();
        return true;
    }
};

//-----------------------------------------------------------------------------

// find matching mappings for a partial address with brute force filtering
struct FilterView : public Value::Index::View {

    using Label = FastSparseMap::HashedLabel;

    size_t                    num_mapped_dims;
    const std::vector<Label> &labels;
    std::vector<size_t>       match_dims;
    std::vector<size_t>       extract_dims;
    std::vector<Label>        query;
    size_t                    pos;

    bool is_match() const {
        for (size_t i = 0; i < query.size(); ++i) {
            if (query[i].hash != labels[pos + match_dims[i]].hash) {
                return false;
            }
        }
        return true;
    }

    FilterView(const FastSparseMap &map, const std::vector<size_t> &match_dims_in)
        : num_mapped_dims(map.num_dims()), labels(map.labels()), match_dims(match_dims_in),
          extract_dims(), query(match_dims.size(), Label()), pos(labels.size())
    {
        auto my_pos = match_dims.begin();
        for (size_t i = 0; i < num_mapped_dims; ++i) {
            if ((my_pos == match_dims.end()) || (*my_pos != i)) {
                extract_dims.push_back(i);
            } else {
                ++my_pos;
            }
        }
        assert(my_pos == match_dims.end());
        assert((match_dims.size() + extract_dims.size()) == num_mapped_dims);
    }

    void lookup(ConstArrayRef<const vespalib::stringref*> addr) override {
        assert(addr.size() == query.size());
        for (size_t i = 0; i < addr.size(); ++i) {
            query[i] = Label(*addr[i]);
        }
        pos = 0;
    }

    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override {
        while (pos < labels.size()) {
            if (is_match()) {
                assert(addr_out.size() == extract_dims.size());
                for (size_t i = 0; i < extract_dims.size(); ++i) {
                    *addr_out[i] = labels[pos + extract_dims[i]].label;
                }
                idx_out = (pos / num_mapped_dims); // is this expensive?
                pos += num_mapped_dims;
                return true;
            }
            pos += num_mapped_dims;
        }
        return false;
    }
};

//-----------------------------------------------------------------------------

// iterate all mappings
struct IterateView : public Value::Index::View {

    using Labels = std::vector<FastSparseMap::HashedLabel>;

    size_t        num_mapped_dims;
    const Labels &labels;
    size_t        pos;

    IterateView(const FastSparseMap &map)
        : num_mapped_dims(map.num_dims()), labels(map.labels()), pos(labels.size()) {}

    void lookup(ConstArrayRef<const vespalib::stringref*>) override {
        pos = 0;
    }

    bool next_result(ConstArrayRef<vespalib::stringref*> addr_out, size_t &idx_out) override {
        if (pos >= labels.size()) {
            return false;
        }
        assert(addr_out.size() == num_mapped_dims);
        for (size_t i = 0; i < num_mapped_dims; ++i) {
            *addr_out[i] = labels[pos + i].label;
        }
        idx_out = (pos / num_mapped_dims); // is this expensive?
        pos += num_mapped_dims;
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
    FastSparseMap map;
    FastValueIndex(size_t num_mapped_dims_in, size_t expected_subspaces_in)
        : map(num_mapped_dims_in, expected_subspaces_in) {}

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

template <typename T>
struct FastValue final : Value, ValueBuilder<T> {

    ValueType my_type;
    size_t my_subspace_size;
    FastValueIndex my_index;
    FastCells<T> my_cells;

    FastValue(const ValueType &type_in, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in)
        : my_type(type_in), my_subspace_size(subspace_size_in),
          my_index(num_mapped_dims_in, expected_subspaces_in),
          my_cells(subspace_size_in * expected_subspaces_in) {}
    ~FastValue() override;
    const ValueType &type() const override { return my_type; }
    const Value::Index &index() const override { return my_index; }
    TypedCells cells() const override { return TypedCells(my_cells.memory, get_cell_type<T>(), my_cells.size); }
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override {
        size_t idx = my_index.map.add_mapping(addr) * my_subspace_size;
        if (__builtin_expect((idx == my_cells.size), true)) {
            return my_cells.add_cells(my_subspace_size);
        } 
        return ArrayRef<T>(my_cells.get(idx), my_subspace_size);
    }
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        if (my_index.map.num_dims() == 0) {
            assert(my_index.map.size() == 1);
        }
        assert(my_cells.size == (my_index.map.size() * my_subspace_size));
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<FastValue<T>>();
        usage.merge(my_index.map.estimate_extra_memory_usage());
        usage.merge(my_cells.estimate_extra_memory_usage());
        return usage;
    }
};
template <typename T> FastValue<T>::~FastValue() = default;

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
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<FastValue<T>>();
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
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>>) final override { return std::make_unique<ScalarValue<T>>(_value); }
};

//-----------------------------------------------------------------------------

template <typename LCT, typename RCT, typename OCT, typename Fun>
const Value &
FastValueIndex::sparse_full_overlap_join(const ValueType &res_type, const Fun &fun,
                                         const FastValueIndex &lhs, const FastValueIndex &rhs,
                                         ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells, Stash &stash)
{
    auto &result = stash.create<FastValue<OCT>>(res_type, lhs.map.num_dims(), 1, lhs.map.size());
    auto &result_map = result.my_index.map;
    lhs.map.each_map_entry([&](auto lhs_subspace, auto hash)
                           {
                               auto rhs_subspace = rhs.map.lookup(hash);
                               if (rhs_subspace != FastSparseMap::npos()) {
                                   auto idx = result_map.add_mapping(lhs.map.make_addr(lhs_subspace), hash);
                                   if (__builtin_expect((idx == result.my_cells.size), true)) {
                                       auto cell_value = fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]);
                                       result.my_cells.push_back_fast(cell_value);
                                   }
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
    using HashedLabelRef = std::reference_wrapper<const FastSparseMap::HashedLabel>;
    size_t num_mapped_dims = addr_sources.size();
    auto &result = stash.create<FastValue<OCT>>(res_type, res_type.count_mapped_dimensions(), 1, lhs.map.size()*rhs.map.size());
    FastSparseMap::HashedLabel empty;
    std::vector<HashedLabelRef> output_addr(num_mapped_dims, empty);
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
        auto l_addr = lhs.map.make_addr(lhs_subspace);
        assert(l_addr.size() == store_lhs_idx.size());
        for (size_t i = 0; i < store_lhs_idx.size(); ++i) {
            size_t addr_idx = store_lhs_idx[i];
            output_addr[addr_idx] = l_addr[i];
        }
        for (size_t rhs_subspace = 0; rhs_subspace < rhs.map.size(); ++rhs_subspace) {
            auto r_addr = rhs.map.make_addr(rhs_subspace);
            assert(r_addr.size() == store_rhs_idx.size());
            for (size_t i = 0; i < store_rhs_idx.size(); ++i) {
                size_t addr_idx = store_rhs_idx[i];
                output_addr[addr_idx] = r_addr[i];
            }
            auto idx = result.my_index.map.add_mapping(ConstArrayRef(output_addr));
            if (__builtin_expect((idx == result.my_cells.size), true)) {
                auto cell_value = fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]);
                result.my_cells.push_back_fast(cell_value);
            }
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
    auto &result = stash.create<FastValue<OCT>>(res_type, lhs.map.num_dims(), 1, lhs.map.size()+rhs.map.size());
    lhs.map.each_map_entry([&](auto lhs_subspace, auto hash)
                           {
                               auto idx = result.my_index.map.add_mapping(lhs.map.make_addr(lhs_subspace), hash);
                               if (__builtin_expect((idx == result.my_cells.size), true)) {
                                   auto rhs_subspace = rhs.map.lookup(hash);
                                   if (rhs_subspace != FastSparseMap::npos()) {
                                       auto cell_value = fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]);
                                       result.my_cells.push_back_fast(cell_value);
                                   } else {
                                       result.my_cells.push_back_fast(lhs_cells[lhs_subspace]);
                                   }
                               }
                           });
    rhs.map.each_map_entry([&](auto rhs_subspace, auto hash)
                           {
                               auto lhs_subspace = lhs.map.lookup(hash);
                               if (lhs_subspace == FastSparseMap::npos()) {
                                   auto idx = result.my_index.map.add_mapping(rhs.map.make_addr(rhs_subspace), hash);
                                   if (__builtin_expect((idx == result.my_cells.size), true)) {
                                       result.my_cells.push_back_fast(rhs_cells[rhs_subspace]);
                                   }
                               }
                           });

    return result;
}

}
