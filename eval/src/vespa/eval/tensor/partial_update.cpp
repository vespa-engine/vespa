// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "partial_update.h"
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <cassert>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.partial_update");

using namespace vespalib::eval;

namespace vespalib::tensor {

namespace {

using join_fun_t = double (*)(double, double);

static constexpr size_t npos() { return -1; }

enum class DimCase {
    MAPPED_MATCH, CONV_TO_INDEXED
};

struct DenseCoords {
    std::vector<size_t> dim_sizes;
    size_t total_size = 1;
    size_t offset;
    size_t dim;
    void clear() { offset = 0; dim = 0; }
    void with(size_t coord) {
        size_t cur = dim_sizes[dim];
        if (coord < cur) {
            if (offset != npos()) {
                offset *= cur;
                offset += coord;
            }
        } else {
            offset = npos();
        }
        ++dim;
    }
    void with(vespalib::stringref label) {
        uint32_t result = 0;
        for (char c : label) {
            if (c < '0' || c > '9') { // bad char
                offset = npos();
                break;
            }
            result = result * 10 + (c - '0');
        }
        with(result);
    }
    void add_dim(size_t sz) {
        dim_sizes.push_back(sz);
        total_size *= sz;
    }
    size_t get() const {
        assert(dim == dim_sizes.size());
        return offset;
    }
    ~DenseCoords();
};
DenseCoords::~DenseCoords() = default;

struct Addresses {
    std::vector<vespalib::stringref> addr;
    std::vector<vespalib::stringref *> next_result_refs;
    std::vector<const vespalib::stringref *> lookup_refs;
    std::vector<size_t> lookup_view_dims;
    Addresses(size_t sz)
        : addr(sz), next_result_refs(sz), lookup_refs(sz), lookup_view_dims(sz)
    {
        for (size_t i = 0; i < sz; ++i) {
            next_result_refs[i] = &addr[i];
            lookup_refs[i] = &addr[i];
            lookup_view_dims[i] = i;
        }
    }
    ~Addresses();
};
Addresses::~Addresses() = default;

struct AddressHandler {
    std::vector<DimCase> how;
    DenseCoords target_coords;
    Addresses for_output;
    Addresses from_modifier;
    bool valid;

    AddressHandler(const ValueType &input_type,
                const ValueType &modifier_type)
        : how(), target_coords(),
          for_output(input_type.count_mapped_dimensions()),
          from_modifier(modifier_type.count_mapped_dimensions()),
          valid(true)
    {
        if (! modifier_type.is_sparse()) {
            LOG(error, "Unexpected non-sparse modifier tensor, type is %s",
                modifier_type.to_spec().c_str());
            valid = false;
            return;
        }
        // analyse dimensions
        auto visitor = overload {
            [&](visit_ranges_either, const auto &) { valid = false; },
            [&](visit_ranges_both, const auto &a, const auto &) {
                how.push_back(a.is_mapped() ? DimCase::MAPPED_MATCH : DimCase::CONV_TO_INDEXED);
            }
        };
        const auto & input_dims = input_type.dimensions();
        const auto & modifier_dims = modifier_type.dimensions();
        visit_ranges(visitor,
                     input_dims.begin(), input_dims.end(),
                     modifier_dims.begin(), modifier_dims.end(),
                     [](const auto &a, const auto &b){ return (a.name < b.name); });
        if ((! valid) ||
            (input_dims.size() != modifier_dims.size()) ||
            (input_dims.size() != how.size()))
        {
            LOG(error, "Value type %s does not match modifier type %s (should have same dimensions)",
                input_type.to_spec().c_str(),
                modifier_type.to_spec().c_str());
            valid = false;
            return;
        }
        for (const auto & dim : input_type.dimensions()) {
            if (dim.is_indexed()) {
                target_coords.add_dim(dim.size);
            }
        }
    }

    void handle_address()
    {
        target_coords.clear();
        auto out = for_output.addr.begin();
        for (size_t i = 0; i < how.size(); ++i) {
            if (how[i] == DimCase::CONV_TO_INDEXED) {
                target_coords.with(from_modifier.addr[i]);
            } else {
                *out++ = from_modifier.addr[i];
            }
        }
        assert(out == for_output.addr.end());
        assert(target_coords.dim == target_coords.dim_sizes.size());
    }

    ~AddressHandler();
};
AddressHandler::~AddressHandler() = default;

template <typename CT>
Value::UP
copy_tensor(const Value &input, const ValueType &input_type, Addresses &helper, const ValueBuilderFactory &factory)
{
    const size_t num_mapped_in_input = input_type.count_mapped_dimensions();
    const size_t dsss = input_type.dense_subspace_size();
    const size_t expected_subspaces = input.index().size();
    auto builder = factory.create_value_builder<CT>(input_type, num_mapped_in_input, dsss, expected_subspaces);
    auto view = input.index().create_view({});
    view->lookup({});
    auto input_cells = input.cells().typify<CT>();
    size_t input_subspace;
    while (view->next_result(helper.next_result_refs, input_subspace)) {
        size_t input_offset = input_subspace * dsss;
        auto src = input_cells.begin() + input_offset;
        auto dst = builder->add_subspace(helper.addr).begin();
        for (size_t i = 0; i < dsss; ++i) {
            dst[i] = src[i];
        }
    }
    return builder->build(std::move(builder));
}

template <typename ICT, typename MCT>
Value::UP
my_modify_value(const Value &input, join_fun_t function, const Value &modifier, const ValueBuilderFactory &factory)
{
    const ValueType &input_type = input.type();
    const size_t dsss = input_type.dense_subspace_size();
    const ValueType &modifier_type = modifier.type();
    AddressHandler handler(input_type, modifier_type);
    if (! handler.valid) {
        return Value::UP();
    }
    // copy input to output
    auto out = copy_tensor<ICT>(input, input_type, handler.for_output, factory);
    // need to overwrite some cells
    auto output_cells = unconstify(out->cells().template typify<ICT>());
    const auto modifier_cells = modifier.cells().typify<MCT>();
    auto modifier_view = modifier.index().create_view({});
    auto lookup_view = out->index().create_view(handler.for_output.lookup_view_dims);
    modifier_view->lookup({});
    size_t modifier_subspace_index;
    while (modifier_view->next_result(handler.from_modifier.next_result_refs, modifier_subspace_index)) {
        handler.handle_address();
        size_t dense_idx = handler.target_coords.get();
        if (dense_idx == npos()) {
            continue;
        }
        lookup_view->lookup(handler.for_output.lookup_refs);
        size_t output_subspace_index;
        if (lookup_view->next_result({}, output_subspace_index)) {
            size_t subspace_offset = dsss * output_subspace_index;
            auto dst = output_cells.begin() + subspace_offset;
            ICT lhs = dst[dense_idx];
            MCT rhs = modifier_cells[modifier_subspace_index];
            dst[dense_idx] = function(lhs, rhs);
        }
    }
    return out;
}
struct PerformModify {
    template<typename ICT, typename MCT>
    static Value::UP invoke(const Value &input,
                            join_fun_t function,
                            const Value &modifier,
                            const ValueBuilderFactory &factory)
    {
        return my_modify_value<ICT,MCT>(input, function, modifier, factory);
    }
};

//-----------------------------------------------------------------------------


template <typename ICT, typename MCT>
Value::UP
my_add_cells(const Value &input, const Value &modifier, const ValueBuilderFactory &factory)
{
    const ValueType &input_type = input.type();
    const ValueType &modifier_type = modifier.type();
    if (input_type.dimensions() != modifier_type.dimensions()) {
        LOG(error, "when adding cells to a tensor, dimensions must be equal");
        return Value::UP();
    }
    const auto input_cells = input.cells().typify<ICT>();
    const auto modifier_cells = modifier.cells().typify<MCT>();
    const size_t num_mapped_in_input = input_type.count_mapped_dimensions();
    const size_t dsss = input_type.dense_subspace_size();
    const size_t expected_subspaces = input.index().size() + modifier.index().size();
    auto builder = factory.create_value_builder<ICT>(input_type, num_mapped_in_input, dsss, expected_subspaces);
    Addresses addrs(num_mapped_in_input);
    std::set<size_t> overwritten_subspaces;
    auto modifier_view = modifier.index().create_view({});
    auto lookup_view = input.index().create_view(addrs.lookup_view_dims);
    modifier_view->lookup({});
    size_t modifier_subspace_index;
    while (modifier_view->next_result(addrs.next_result_refs, modifier_subspace_index)) {
        size_t modifier_offset = dsss * modifier_subspace_index;
        auto src = modifier_cells.begin() + modifier_offset;
        auto dst = builder->add_subspace(addrs.addr).begin();
        for (size_t i = 0; i < dsss; ++i) {
            dst[i] = src[i];
        }
        lookup_view->lookup(addrs.lookup_refs);
        size_t input_subspace_index;
        if (lookup_view->next_result({}, input_subspace_index)) {
            overwritten_subspaces.insert(input_subspace_index);
        }
    }
    auto input_view = input.index().create_view({});
    input_view->lookup({});
    size_t input_subspace_index;
    while (input_view->next_result(addrs.next_result_refs, input_subspace_index)) {
        if (overwritten_subspaces.count(input_subspace_index) == 0) {
            size_t input_offset = dsss * input_subspace_index;
            auto src = input_cells.begin() + input_offset;
            auto dst = builder->add_subspace(addrs.addr).begin();
            for (size_t i = 0; i < dsss; ++i) {
                dst[i] = src[i];
            }
        }
    }
    return builder->build(std::move(builder));
}

struct PerformAdd {
    template<typename ICT, typename MCT>
    static Value::UP invoke(const Value &input,
                            const Value &modifier,
                            const ValueBuilderFactory &factory)
    {
        return my_add_cells<ICT,MCT>(input, modifier, factory);
    }
};

//-----------------------------------------------------------------------------

template <typename ICT>
Value::UP
my_remove_cells(const Value &input, const Value &modifier, const ValueBuilderFactory &factory)
{
    const ValueType &input_type = input.type();
    const ValueType &modifier_type = modifier.type();
    if (input_type.mapped_dimensions() != modifier_type.mapped_dimensions()) {
        LOG(error, "when removing cells from a tensor, mapped dimensions must be equal");
        return Value::UP();
    }
    if (input_type.mapped_dimensions().size() == 0) {
        LOG(error, "cannot remove cells from a dense tensor");
        return Value::UP();
    }
    const auto input_cells = input.cells().typify<ICT>();
    const size_t num_mapped_in_input = input_type.count_mapped_dimensions();
    const size_t dsss = input_type.dense_subspace_size();
    Addresses addrs(num_mapped_in_input);
    std::set<size_t> removed_subspaces;
    auto modifier_view = modifier.index().create_view({});
    auto lookup_view = input.index().create_view(addrs.lookup_view_dims);
    modifier_view->lookup({});
    size_t modifier_subspace_index;
    while (modifier_view->next_result(addrs.next_result_refs, modifier_subspace_index)) {
        lookup_view->lookup(addrs.lookup_refs);
        size_t input_subspace_index;
        if (lookup_view->next_result({}, input_subspace_index)) {
            removed_subspaces.insert(input_subspace_index);
        }
    }
    const size_t expected_subspaces = input.index().size() - removed_subspaces.size();
    auto builder = factory.create_value_builder<ICT>(input_type, num_mapped_in_input, dsss, expected_subspaces);
    auto input_view = input.index().create_view({});
    input_view->lookup({});
    size_t input_subspace_index;
    while (input_view->next_result(addrs.next_result_refs, input_subspace_index)) {
        if (removed_subspaces.count(input_subspace_index) == 0) {
            size_t input_offset = dsss * input_subspace_index;
            auto src = input_cells.begin() + input_offset;
            auto dst = builder->add_subspace(addrs.addr).begin();
            for (size_t i = 0; i < dsss; ++i) {
                dst[i] = src[i];
            }
        }
    }
    return builder->build(std::move(builder));
}

struct PerformRemove {
    template<typename ICT>
    static Value::UP invoke(const Value &input,
                            const Value &modifier,
                            const ValueBuilderFactory &factory)
    {
        return my_remove_cells<ICT>(input, modifier, factory);
    }
};

} // namespace <unnamed>

//-----------------------------------------------------------------------------

Value::UP
TensorPartialUpdate::modify(const Value &input, join_fun_t function,
                            const Value &modifier, const ValueBuilderFactory &factory)
{
    return typify_invoke<2, TypifyCellType, PerformModify>(
            input.cells().type, modifier.cells().type,
            input, function, modifier, factory);
}

Value::UP
TensorPartialUpdate::add(const Value &input, const Value &add_cells, const ValueBuilderFactory &factory)
{
    return typify_invoke<2, TypifyCellType, PerformAdd>(
            input.cells().type, add_cells.cells().type,
            input, add_cells, factory);
}

Value::UP
TensorPartialUpdate::remove(const Value &input, const Value &remove_spec, const ValueBuilderFactory &factory)
{
    return typify_invoke<1, TypifyCellType, PerformRemove>(
            input.cells().type,
            input, remove_spec, factory);
}

} // namespace
