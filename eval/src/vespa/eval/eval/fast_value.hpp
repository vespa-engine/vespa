// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_value.h"
#include "inline_operation.h"
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace vespalib::eval {

//-----------------------------------------------------------------------------

// This is the class instructions will look for when optimizing sparse
// operations by calling inline functions directly.

struct FastValueIndex final : SimpleValueIndex {
    FastValueIndex(size_t num_mapped_dims_in, size_t expected_subspaces_in)
        : SimpleValueIndex(num_mapped_dims_in, expected_subspaces_in) {}

    template <typename LCT, typename RCT, typename OCT, typename Fun>
        static const Value &sparse_full_overlap_join(const ValueType &res_type, const Fun &fun,
                const FastValueIndex &lhs, const FastValueIndex &rhs,
                ConstArrayRef<LCT> lhs_cells, ConstArrayRef<RCT> rhs_cells, Stash &stash);
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
        ValueBuilder<T>* me = this;
        assert(me == self.get());
        self.release();
        return std::unique_ptr<Value>(this);
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
    lhs.map.each_map_entry([&](auto addr_tag, auto lhs_subspace, auto hash)
                           {
                               auto rhs_subspace = rhs.map.lookup(hash);
                               if (rhs_subspace != SimpleSparseMap::npos()) {
                                   result.my_index.map.add_mapping(lhs.map.make_addr(addr_tag), hash);
                                   result.my_cells.push_back(fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]));
                               }
                           });
    return result;
}

//-----------------------------------------------------------------------------

}
