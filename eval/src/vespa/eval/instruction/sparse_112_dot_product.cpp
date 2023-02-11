// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_112_dot_product.h"
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/eval/eval/visit_stuff.h>
#include <algorithm>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

template <typename T, size_t N>
ConstArrayRef<T> as_car(std::array<T, N> &array) noexcept {
    return {array.data(), array.size()};
}

template <typename T, size_t N>
ConstArrayRef<const T *> as_ccar(std::array<T *, N> &array) noexcept {
    return {array.data(), array.size()};
}

template <typename T>
ConstArrayRef<T> as_car(T &value) noexcept {
    return {&value, 1};
}

constexpr std::array<size_t, 2> both_dims = { 0, 1 };

template <typename CT>
double my_sparse_112_dot_product_fallback(const Value::Index &a_idx, const Value::Index &b_idx, const Value::Index &c_idx,
                                          const CT *a_cells, const CT *b_cells, const CT *c_cells) __attribute__((noinline));
template <typename CT>
double my_sparse_112_dot_product_fallback(const Value::Index &a_idx, const Value::Index &b_idx, const Value::Index &c_idx,
                                          const CT *a_cells, const CT *b_cells, const CT *c_cells)
{
    double result = 0.0;
    size_t a_space = 0;
    size_t b_space = 0;
    size_t c_space = 0;
    std::array<string_id, 2> c_addr;
    std::array<string_id*, 2> c_addr_ref = {&c_addr[0], &c_addr[1]};
    auto outer = a_idx.create_view({});
    auto inner = b_idx.create_view({});
    auto model = c_idx.create_view({&both_dims[0], 2});
    outer->lookup({});
    while (outer->next_result(as_car(c_addr_ref[0]), a_space)) {
        inner->lookup({});
        while (inner->next_result(as_car(c_addr_ref[1]), b_space)) {
            model->lookup(as_ccar(c_addr_ref));
            if (model->next_result({}, c_space)) {
                result += (a_cells[a_space] * b_cells[b_space] * c_cells[c_space]);
            }
        }
    }
    return result;
}

template <typename CT>
double my_fast_sparse_112_dot_product(const FastAddrMap *a_map, const FastAddrMap *b_map, const FastAddrMap *c_map,
                                      const CT *a_cells, const CT *b_cells, const CT *c_cells) __attribute__((noinline));
template <typename CT>
double my_fast_sparse_112_dot_product(const FastAddrMap *a_map, const FastAddrMap *b_map, const FastAddrMap *c_map,
                                      const CT *a_cells, const CT *b_cells, const CT *c_cells)
{
    double result = 0.0;
    std::array<string_id, 2> c_addr;
    const auto &a_labels = a_map->labels();
    for (size_t a_space = 0; a_space < a_labels.size(); ++a_space) {
        if (a_cells[a_space] != 0.0) { // handle pseudo-sparse input
            c_addr[0] = a_labels[a_space];
            const auto &b_labels = b_map->labels();
            for (size_t b_space = 0; b_space < b_labels.size(); ++b_space) {
                if (b_cells[b_space] != 0.0) { // handle pseudo-sparse input
                    c_addr[1] = b_labels[b_space];
                    auto c_space = c_map->lookup(as_car(c_addr));
                    if (c_space != FastAddrMap::npos()) {
                        result += (a_cells[a_space] * b_cells[b_space] * c_cells[c_space]);
                    }
                }
            }
        }
    }
    return result;
}

template <typename CT>
void my_sparse_112_dot_product_op(InterpretedFunction::State &state, uint64_t) {
    const auto &a_idx = state.peek(2).index();
    const auto &b_idx = state.peek(1).index();
    const auto &c_idx = state.peek(0).index();
    const CT *a_cells = state.peek(2).cells().unsafe_typify<CT>().cbegin();
    const CT *b_cells = state.peek(1).cells().unsafe_typify<CT>().cbegin();
    const CT *c_cells = state.peek(0).cells().unsafe_typify<CT>().cbegin();
    double result = __builtin_expect(are_fast(a_idx, b_idx, c_idx), true)
        ? my_fast_sparse_112_dot_product<CT>(&as_fast(a_idx).map, &as_fast(b_idx).map, &as_fast(c_idx).map,
                                             a_cells, b_cells, c_cells)
        : my_sparse_112_dot_product_fallback<CT>(a_idx, b_idx, c_idx, a_cells, b_cells, c_cells);
    state.pop_pop_pop_push(state.stash.create<DoubleValue>(result));
}

struct MyGetFun {
    template <typename CT>
    static auto invoke() { return my_sparse_112_dot_product_op<CT>; }
};

using MyTypify = TypifyValue<TypifyCellType>;

// Try to collect input nodes and organize them into a dot product
// between (n sparse non-overlapping single-dimension tensors) and (a
// sparse n-dimensional tensor) all having the same cell type.

struct InputState {
    std::vector<const TensorFunction *> single;
    const TensorFunction *multi = nullptr;
    bool collision = false;

    void collect(const TensorFunction &node) {
        const auto &type = node.result_type();
        if (type.is_sparse()) {
            if (type.dimensions().size() == 1) {
                single.push_back(&node);
            } else {
                if (multi) {
                    collision = true;
                } else {
                    multi = &node;
                }
            }
        }
    }
    void finalize() {
        std::sort(single.begin(), single.end(), [](const auto *a, const auto *b)
                  { return (a->result_type().dimensions()[0].name < b->result_type().dimensions()[0].name); });
    }
    bool verify(size_t n) const {
        if (collision || (single.size() != n) || (multi == nullptr) || (multi->result_type().dimensions().size() != n)) {
            return false;
        }
        const auto &multi_type = multi->result_type();
        for (size_t i = 0; i < n; ++i) {
            const auto &single_type = single[i]->result_type();
            if ((single_type.cell_type() != multi_type.cell_type()) ||
                (single_type.dimensions()[0].name != multi_type.dimensions()[i].name))
            {
                return false;
            }
        }
        return true;
    }
};

// Try to find inputs that form a 112 dot product.

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
            state.finalize();
            if (state.verify(2)) {
                a = state.single[0];
                b = state.single[1];
                c = state.multi;
                return true;
            }
        }
        return false;
    }
};

} // namespace <unnamed>

Sparse112DotProduct::Sparse112DotProduct(const TensorFunction &a_in,
                                         const TensorFunction &b_in,
                                         const TensorFunction &c_in)
  : tensor_function::Node(DoubleValue::shared_type()),
    _a(a_in),
    _b(b_in),
    _c(c_in)
{
}

InterpretedFunction::Instruction
Sparse112DotProduct::compile_self(const ValueBuilderFactory &, Stash &) const
{
    REQUIRE_EQ(_a.get().result_type().cell_type(), _b.get().result_type().cell_type());
    REQUIRE_EQ(_a.get().result_type().cell_type(), _c.get().result_type().cell_type());
    auto op = typify_invoke<1,MyTypify,MyGetFun>(_a.get().result_type().cell_type());
    return InterpretedFunction::Instruction(op);
}

void
Sparse112DotProduct::push_children(std::vector<Child::CREF> &children) const
{
    children.emplace_back(_a);
    children.emplace_back(_b);
    children.emplace_back(_c);
}

void
Sparse112DotProduct::visit_children(vespalib::ObjectVisitor &visitor) const
{
    ::visit(visitor, "a", _a.get());
    ::visit(visitor, "b", _b.get());
    ::visit(visitor, "c", _c.get());
}

const TensorFunction &
Sparse112DotProduct::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM) && expr.result_type().is_double()) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            FindInputs inputs;
            if (inputs.try_match(join->lhs(), join->rhs()) ||
                inputs.try_match(join->rhs(), join->lhs()))
            {
                return stash.create<Sparse112DotProduct>(*inputs.a, *inputs.b, *inputs.c);
            }
        }
    }
    return expr;
}

} // namespace
