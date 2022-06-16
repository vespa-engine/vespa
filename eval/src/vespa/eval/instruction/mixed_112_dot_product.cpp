// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mixed_112_dot_product.h"
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/eval/eval/visit_stuff.h>
#include <cblas.h>
#include <algorithm>
#include <optional>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

template <typename CT> double my_dot_product(const CT * lhs, const CT * rhs, size_t count);
template <> double my_dot_product<double>(const double * lhs, const double * rhs, size_t count) {
    return cblas_ddot(count, lhs, 1, rhs, 1);
}
template <> double my_dot_product<float>(const float * lhs, const float * rhs, size_t count) {
    return cblas_sdot(count, lhs, 1, rhs, 1);
}

template <typename T, size_t N>
ConstArrayRef<const T *> as_ccar(std::array<T *, N> &array) {
    return {array.data(), array.size()};
}

template <typename T>
ConstArrayRef<T> as_car(T &value) {
    return {&value, 1};
}

constexpr std::array<size_t, 1> single_dim = { 0 };

template <typename CT>
double my_mixed_112_dot_product_fallback(const Value::Index &a_idx, const Value::Index &c_idx,
                                         const CT *a_cells, const CT *b_cells, const CT *c_cells,
                                         size_t dense_size) __attribute__((noinline));
template <typename CT>
double my_mixed_112_dot_product_fallback(const Value::Index &a_idx, const Value::Index &c_idx,
                                         const CT *a_cells, const CT *b_cells, const CT *c_cells,
                                         size_t dense_size)
{
    double result = 0.0;
    size_t a_space = 0;
    size_t c_space = 0;
    std::array<string_id, 1> c_addr;
    std::array<string_id*, 1> c_addr_ref = {&c_addr[0]};
    auto outer = a_idx.create_view({});
    auto model = c_idx.create_view({&single_dim[0], 1});
    outer->lookup({});
    while (outer->next_result(as_car(c_addr_ref[0]), a_space)) {
        model->lookup(as_ccar(c_addr_ref));
        if (model->next_result({}, c_space)) {
            result += my_dot_product<CT>(b_cells, c_cells + (c_space * dense_size), dense_size) * a_cells[a_space];
        }
    }
    return result;
}

template <typename CT>
double my_fast_mixed_112_dot_product(const FastAddrMap *a_map, const FastAddrMap *c_map,
                                     const CT *a_cells, const CT *b_cells, const CT *c_cells,
                                     size_t dense_size)
{
    double result = 0.0;
    const auto &a_labels = a_map->labels();
    for (size_t a_space = 0; a_space < a_labels.size(); ++a_space) {
        if (a_cells[a_space] != 0.0) { // handle pseudo-sparse input
            auto c_space = c_map->lookup_singledim(a_labels[a_space]);
            if (c_space != FastAddrMap::npos()) {
                result += my_dot_product<CT>(b_cells, c_cells + (c_space * dense_size), dense_size) * a_cells[a_space];
            }
        }
    }
    return result;
}

template <typename CT>
void my_mixed_112_dot_product_op(InterpretedFunction::State &state, uint64_t dense_size) {
    const auto &a_idx = state.peek(2).index();
    const auto &c_idx = state.peek(0).index();
    const CT *a_cells = state.peek(2).cells().unsafe_typify<CT>().cbegin();
    const CT *b_cells = state.peek(1).cells().unsafe_typify<CT>().cbegin();
    const CT *c_cells = state.peek(0).cells().unsafe_typify<CT>().cbegin();
    double result = __builtin_expect(are_fast(a_idx, c_idx), true)
        ? my_fast_mixed_112_dot_product<CT>(&as_fast(a_idx).map, &as_fast(c_idx).map,
                                            a_cells, b_cells, c_cells, dense_size)
        : my_mixed_112_dot_product_fallback<CT>(a_idx, c_idx, a_cells, b_cells, c_cells, dense_size);
    state.pop_pop_pop_push(state.stash.create<DoubleValue>(result));
}

InterpretedFunction::op_function my_select(CellType cell_type) {
    if (cell_type == CellType::DOUBLE) {
        return my_mixed_112_dot_product_op<double>;
    }
    if (cell_type == CellType::FLOAT) {
        return my_mixed_112_dot_product_op<float>;
    }
    abort();
}

// Try to collect input nodes and organize them into a 3-way dot
// product between one 1d sparse tensor, one 1d dense tensor and one
// 2d mixed tensor. Cell types must be all float or all double.

struct InputState {
    std::optional<CellType> cell_type;
    const TensorFunction *sparse = nullptr;
    const TensorFunction *dense = nullptr;
    const TensorFunction *mixed = nullptr;
    bool failed = false;

    void collect_cell_type(CellType ct) {
        if (cell_type.has_value()) {
            if (ct != cell_type.value()) {
                failed = true;
            }
        } else {
            cell_type = ct;
        }
    }

    void try_save(const TensorFunction *&my_ptr, const TensorFunction &node) {
        if (my_ptr == nullptr) {
            my_ptr = &node;
        } else {
            failed = true;
        }
    }

    void collect(const TensorFunction &node) {
        const auto &type = node.result_type();
        collect_cell_type(type.cell_type());
        if (type.is_sparse()) {
            try_save(sparse, node);
        } else if (type.is_dense()) {
            try_save(dense, node);
        } else if (type.has_dimensions()) {
            try_save(mixed, node);
        } else {
            failed = true;
        }
    }

    bool verify() const {
        // all parts must have been collected successfully
        if (failed || !cell_type.has_value() || (sparse == nullptr) || (dense == nullptr) || (mixed == nullptr)) {
            return false;
        }
        // common cell type must be appropriate
        if ((cell_type.value() != CellType::FLOAT) && (cell_type.value() != CellType::DOUBLE)) {
            return false;
        }
        // number of dimensions must match the expected 112 pattern
        if ((sparse->result_type().dimensions().size() != 1) ||
            (dense->result_type().dimensions().size() != 1) ||
            (mixed->result_type().dimensions().size() != 2))
        {
            return false;
        }
        // the product of the sparse and dense tensors must fully overlap the mixed tensor
        const ValueType::Dimension *mapped = &mixed->result_type().dimensions()[0];
        const ValueType::Dimension *indexed = &mixed->result_type().dimensions()[1];
        if (!mapped->is_mapped()) {
            std::swap(mapped, indexed);
        }
        assert(mapped->is_mapped());
        assert(indexed->is_indexed());
        return ((*mapped == sparse->result_type().dimensions()[0]) &&
                (*indexed == dense->result_type().dimensions()[0]));
    }
};

// Try to find inputs that form a 112 mixed dot product.

struct FindInputs {
    const TensorFunction *a = nullptr;
    const TensorFunction *b = nullptr;
    const TensorFunction *c = nullptr;

    bool try_match(const TensorFunction &one, const TensorFunction &two) {
        auto join = as<Join>(two);
        if (join && (join->function() == Mul::f)) {
            InputState state;
            state.collect(one);
            state.collect(join->lhs());
            state.collect(join->rhs());
            if (state.verify()) {
                a = state.sparse;
                b = state.dense;
                c = state.mixed;
                return true;
            }
        }
        return false;
    }
};

} // namespace <unnamed>

Mixed112DotProduct::Mixed112DotProduct(const TensorFunction &a_in,
                                       const TensorFunction &b_in,
                                       const TensorFunction &c_in)
  : tensor_function::Node(DoubleValue::shared_type()),
    _a(a_in),
    _b(b_in),
    _c(c_in)
{
}

InterpretedFunction::Instruction
Mixed112DotProduct::compile_self(const ValueBuilderFactory &, Stash &) const
{
    REQUIRE_EQ(_a.get().result_type().cell_type(), _b.get().result_type().cell_type());
    REQUIRE_EQ(_a.get().result_type().cell_type(), _c.get().result_type().cell_type());
    REQUIRE_EQ(_b.get().result_type().dense_subspace_size(), _c.get().result_type().dense_subspace_size());
    auto op = my_select(_a.get().result_type().cell_type());
    return InterpretedFunction::Instruction(op, _c.get().result_type().dense_subspace_size());
}

void
Mixed112DotProduct::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_a);
    children.emplace_back(_b);
    children.emplace_back(_c);
}

void
Mixed112DotProduct::visit_children(vespalib::ObjectVisitor &visitor) const
{
    ::visit(visitor, "a", _a.get());
    ::visit(visitor, "b", _b.get());
    ::visit(visitor, "c", _c.get());
}

const TensorFunction &
Mixed112DotProduct::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM) && expr.result_type().is_double()) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            FindInputs inputs;
            if (inputs.try_match(join->lhs(), join->rhs()) ||
                inputs.try_match(join->rhs(), join->lhs()))
            {
                return stash.create<Mixed112DotProduct>(*inputs.a, *inputs.b, *inputs.c);
            }
        }
    }
    return expr;
}

} // namespace
