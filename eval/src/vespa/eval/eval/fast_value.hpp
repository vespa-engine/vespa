// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value.h"
#include "fast_sparse_map.h"
#include "inline_operation.h"
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

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
        static const Value &sparse_only_merge(const ValueType &res_type, const Fun &fun,
                                         const FastValueIndex &lhs, const FastValueIndex &rhs,
                                         ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells,
                                         Stash &stash) __attribute((noinline));

    size_t size() const override { return map.size(); }
    std::unique_ptr<View> create_view(const std::vector<size_t> &dims) const override;
};

//-----------------------------------------------------------------------------

template <typename T>
struct FastValue final : Value, ValueBuilder<T> {

    ValueType my_type;
    size_t my_subspace_size;
    FastValueIndex my_index;
    std::vector<T> my_cells;

    FastValue(const ValueType &type_in, size_t num_mapped_dims_in, size_t subspace_size_in, size_t expected_subspaces_in)
        : my_type(type_in), my_subspace_size(subspace_size_in), my_index(num_mapped_dims_in, expected_subspaces_in), my_cells()
    {
        my_cells.reserve(subspace_size_in * expected_subspaces_in);
    }
    ~FastValue() override;
    const ValueType &type() const override { return my_type; }
    const Value::Index &index() const override { return my_index; }
    TypedCells cells() const override { return TypedCells(ConstArrayRef<T>(my_cells)); }
    ArrayRef<T> add_subspace(ConstArrayRef<vespalib::stringref> addr) override {
        size_t old_size = my_cells.size();
        my_index.map.add_mapping(addr);
        my_cells.resize(old_size + my_subspace_size);
        return ArrayRef<T>(&my_cells[old_size], my_subspace_size);
    }
    std::unique_ptr<Value> build(std::unique_ptr<ValueBuilder<T>> self) override {
        if (my_index.map.num_dims() == 0) {
            assert(my_index.map.size() == 1);
        }
        assert(my_cells.size() == (my_index.map.size() * my_subspace_size));
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
    }
    MemoryUsage get_memory_usage() const override {
        MemoryUsage usage = self_memory_usage<FastValue<T>>();
        usage.merge(vector_extra_memory_usage(my_cells));
        usage.merge(my_index.map.estimate_extra_memory_usage());
        return usage;
    }
};
template <typename T> FastValue<T>::~FastValue() = default;

//-----------------------------------------------------------------------------

template <typename LCT, typename RCT, typename OCT, typename Fun>
const Value &
FastValueIndex::sparse_full_overlap_join(const ValueType &res_type, const Fun &fun,
                                         const FastValueIndex &lhs, const FastValueIndex &rhs,
                                         ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells, Stash &stash)
{
    auto &result = stash.create<FastValue<OCT>>(res_type, lhs.map.num_dims(), 1, lhs.map.size());
    lhs.map.each_map_entry([&](auto lhs_subspace, auto hash)
                           {
                               auto rhs_subspace = rhs.map.lookup(hash);
                               if (rhs_subspace != FastSparseMap::npos()) {
                                   result.my_index.map.add_mapping(lhs.map.make_addr(lhs_subspace), hash);
                                   result.my_cells.push_back(fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]));
                               }
                           });
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
                               auto rhs_subspace = rhs.map.lookup(hash);
                               result.my_index.map.add_mapping(lhs.map.make_addr(lhs_subspace), hash);
                               if (rhs_subspace != FastSparseMap::npos()) {
                                   result.my_cells.push_back(fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]));
                               } else {
                                   result.my_cells.push_back(lhs_cells[lhs_subspace]);
                               }
                           });
    rhs.map.each_map_entry([&](auto rhs_subspace, auto hash)
                           {
                               auto lhs_subspace = lhs.map.lookup(hash);
                               if (lhs_subspace == FastSparseMap::npos()) {
                                   result.my_index.map.add_mapping(rhs.map.make_addr(rhs_subspace), hash);
                                   result.my_cells.push_back(rhs_cells[rhs_subspace]);
                               }
                           });

    return result;
}

//-----------------------------------------------------------------------------

}
