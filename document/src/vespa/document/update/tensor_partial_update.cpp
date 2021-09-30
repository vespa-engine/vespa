// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_partial_update.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <cassert>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.partial_update");

using namespace vespalib;
using namespace vespalib::eval;

namespace document {

namespace {

using join_fun_t = vespalib::eval::operation::op2_t;

static constexpr size_t npos() { return -1; }

enum class DimCase {
    MAPPED_MATCH, CONV_TO_INDEXED
};

struct DenseCoords {
    std::vector<size_t> dim_sizes;
    size_t total_size;
    size_t offset;
    size_t current;
    DenseCoords(const ValueType &output_type)
        : total_size(1), offset(0), current(0)
    {
        for (const auto & dim : output_type.dimensions()) {
            if (dim.is_indexed()) {
                dim_sizes.push_back(dim.size);
                total_size *= dim.size;
            }
        }
    }
    ~DenseCoords();
    void clear() { offset = 0; current = 0; }
    void convert_label(string_id label_id) {
        vespalib::string label = SharedStringRepo::Handle::string_from_id(label_id);
        uint32_t coord = 0;
        for (char c : label) {
            if (c < '0' || c > '9') { // bad char
                offset = npos();
                break;
            }
            coord = coord * 10 + (c - '0');
        }
        size_t cur_dim_size = dim_sizes[current];
        if (coord < cur_dim_size) {
            if (offset != npos()) {
                offset *= cur_dim_size;
                offset += coord;
            }
        } else {
            offset = npos();
        }
        ++current;
    }
    size_t get_dense_index() const {
        assert(current == dim_sizes.size());
        return offset;
    }
};
DenseCoords::~DenseCoords() = default;

struct SparseCoords {
    std::vector<string_id> addr;
    std::vector<string_id *> next_result_refs;
    std::vector<const string_id *> lookup_refs;
    std::vector<size_t> lookup_view_dims;
    SparseCoords(size_t sz)
        : addr(sz), next_result_refs(sz), lookup_refs(sz), lookup_view_dims(sz)
    {
        for (size_t i = 0; i < sz; ++i) {
            next_result_refs[i] = &addr[i];
            lookup_refs[i] = &addr[i];
            lookup_view_dims[i] = i;
        }
    }
    ~SparseCoords();
};
SparseCoords::~SparseCoords() = default;

/**
 * Helper class that converts a fully-sparse address from the modifier
 * tensor into a subset sparse address for the output and an offset
 * in the dense subspace.
 **/
struct AddressHandler {
    std::vector<DimCase> dimension_plan;
    DenseCoords dense_converter;
    SparseCoords for_output;
    SparseCoords from_modifier;
    bool valid;

    AddressHandler(const ValueType &output_type,
                   const ValueType &modifier_type)
        : dimension_plan(), dense_converter(output_type),
          for_output(output_type.count_mapped_dimensions()),
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
                dimension_plan.push_back(a.is_mapped() ? DimCase::MAPPED_MATCH : DimCase::CONV_TO_INDEXED);
            }
        };
        const auto & input_dims = output_type.dimensions();
        const auto & modifier_dims = modifier_type.dimensions();
        visit_ranges(visitor,
                     input_dims.begin(), input_dims.end(),
                     modifier_dims.begin(), modifier_dims.end(),
                     [](const auto &a, const auto &b){ return (a.name < b.name); });
        if (! valid) {
            LOG(error, "Value type %s does not match modifier type %s (should have same dimensions)",
                output_type.to_spec().c_str(), modifier_type.to_spec().c_str());
            return;
        }
        // implicitly checked above, must hold:
        assert(input_dims.size() == modifier_dims.size());
        // the plan should now be fully built:
        assert(input_dims.size() == dimension_plan.size());
    }

    void handle_address()
    {
        dense_converter.clear();
        auto out = for_output.addr.begin();
        for (size_t i = 0; i < dimension_plan.size(); ++i) {
            if (dimension_plan[i] == DimCase::CONV_TO_INDEXED) {
                dense_converter.convert_label(from_modifier.addr[i]);
            } else {
                *out++ = from_modifier.addr[i];
            }
        }
        assert(out == for_output.addr.end());
        assert(dense_converter.current == dense_converter.dim_sizes.size());
    }

    ~AddressHandler();
};
AddressHandler::~AddressHandler() = default;

template <typename CT, typename ICT = CT, typename KeepFun>
void copy_tensor_with_filter(const Value &input,
                             size_t dsss,
                             SparseCoords &addrs,
                             ValueBuilder<CT> &builder,
                             KeepFun && keep_subspace)
{
    const auto input_cells = input.cells().typify<ICT>();
    auto input_view = input.index().create_view({});
    input_view->lookup({});
    size_t input_subspace_index;
    while (input_view->next_result(addrs.next_result_refs, input_subspace_index)) {
        if (keep_subspace(addrs.lookup_refs, input_subspace_index)) {
            size_t input_offset = dsss * input_subspace_index;
            auto src = input_cells.begin() + input_offset;
            auto dst = builder.add_subspace(addrs.addr).begin();
            for (size_t i = 0; i < dsss; ++i) {
                dst[i] = src[i];
            }
        }
    }
}

template <typename CT>
Value::UP
copy_tensor(const Value &input, const ValueType &input_type, SparseCoords &helper, const ValueBuilderFactory &factory)
{
    const size_t num_mapped_in_input = input_type.count_mapped_dimensions();
    const size_t dsss = input_type.dense_subspace_size();
    const size_t expected_subspaces = input.index().size();
    auto builder = factory.create_value_builder<CT>(input_type, num_mapped_in_input, dsss, expected_subspaces);
    auto no_filter = [] (const auto &, size_t) {
        return true;
    };
    copy_tensor_with_filter<CT>(input, dsss, helper, *builder, no_filter);
    return builder->build(std::move(builder));
}

//-----------------------------------------------------------------------------

struct PerformModify {
    template<typename ICT, typename MCT>
    static Value::UP invoke(const Value &input,
                            join_fun_t function,
                            const Value &modifier,
                            const ValueBuilderFactory &factory);
};

template <typename ICT, typename MCT>
Value::UP
PerformModify::invoke(const Value &input, join_fun_t function, const Value &modifier, const ValueBuilderFactory &factory)
{
    const ValueType &input_type = input.type();
    const size_t dsss = input_type.dense_subspace_size();
    const ValueType &modifier_type = modifier.type();
    AddressHandler handler(input_type, modifier_type);
    if (! handler.valid) {
        return {};
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
        size_t dense_idx = handler.dense_converter.get_dense_index();
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

//-----------------------------------------------------------------------------

struct PerformAdd {
    template<typename ICT, typename MCT>
    static Value::UP invoke(const Value &input,
                            const Value &modifier,
                            const ValueBuilderFactory &factory);
};

template <typename ICT, typename MCT>
Value::UP
PerformAdd::invoke(const Value &input, const Value &modifier, const ValueBuilderFactory &factory)
{
    const ValueType &input_type = input.type();
    const ValueType &modifier_type = modifier.type();
    if (input_type.dimensions() != modifier_type.dimensions()) {
        LOG(error, "when adding cells to a tensor, dimensions must be equal. "
            "Got input type %s != modifier type %s",
            input_type.to_spec().c_str(), modifier_type.to_spec().c_str());
        return {};
    }
    const size_t num_mapped_in_input = input_type.count_mapped_dimensions();
    const size_t dsss = input_type.dense_subspace_size();
    const size_t expected_subspaces = input.index().size() + modifier.index().size();
    auto builder = factory.create_value_builder<ICT>(input_type, num_mapped_in_input, dsss, expected_subspaces);
    SparseCoords addrs(num_mapped_in_input);
    auto lookup_view = input.index().create_view(addrs.lookup_view_dims);
    std::vector<bool> overwritten(input.index().size(), false);
    auto remember_subspaces = [&] (const auto & lookup_refs, size_t) {
        lookup_view->lookup(lookup_refs);
        size_t input_subspace_index;
        if (lookup_view->next_result({}, input_subspace_index)) {
            overwritten[input_subspace_index] = true;
        }
        return true;
    };
    copy_tensor_with_filter<ICT, MCT>(modifier, dsss, addrs, *builder, remember_subspaces);
    auto filter = [&] (const auto &, size_t input_subspace) {
        return ! overwritten[input_subspace];
    };
    copy_tensor_with_filter<ICT>(input, dsss, addrs, *builder, filter);
    return builder->build(std::move(builder));
}

//-----------------------------------------------------------------------------

struct PerformRemove {
    template<typename ICT>
    static Value::UP invoke(const Value &input,
                            const Value &modifier,
                            const ValueBuilderFactory &factory);
};

/**
 * Calculates the indexes of where the mapped modifier dimensions are found in the mapped input dimensions.
 *
 * The modifier dimensions should be a subset or all of the input dimensions.
 * An empty vector is returned on type mismatch.
 */
std::vector<size_t>
calc_mapped_dimension_indexes(const ValueType& input_type,
                              const ValueType& modifier_type)
{
    auto input_dims = input_type.mapped_dimensions();
    auto mod_dims = modifier_type.mapped_dimensions();
    if (mod_dims.size() > input_dims.size()) {
        return {};
    }
    std::vector<size_t> result(mod_dims.size());
    size_t j = 0;
    for (size_t i = 0; i < mod_dims.size(); ++i) {
        while ((j < input_dims.size()) && (input_dims[j] != mod_dims[i])) {
            ++j;
        }
        if (j >= input_dims.size()) {
            return {};
        }
        result[i] = j;
    }
    return result;
}

struct ModifierCoords {

    std::vector<const string_id *> lookup_refs;
    std::vector<size_t> lookup_view_dims;

    ModifierCoords(const SparseCoords& input_coords,
                   const std::vector<size_t>& input_dim_indexes,
                   const ValueType& modifier_type)
        : lookup_refs(modifier_type.dimensions().size()),
          lookup_view_dims(modifier_type.dimensions().size())
    {
        assert(modifier_type.dimensions().size() == input_dim_indexes.size());
        for (size_t i = 0; i < input_dim_indexes.size(); ++i) {
            // Setup the modifier dimensions to point to the matching input dimensions.
            lookup_refs[i] = &input_coords.addr[input_dim_indexes[i]];
            lookup_view_dims[i] = i;
        }
    }
    ~ModifierCoords() {}
};

template <typename ICT>
Value::UP
PerformRemove::invoke(const Value &input, const Value &modifier, const ValueBuilderFactory &factory)
{
    const ValueType &input_type = input.type();
    const ValueType &modifier_type = modifier.type();
    const size_t num_mapped_in_input = input_type.count_mapped_dimensions();
    if (num_mapped_in_input == 0) {
        LOG(error, "Cannot remove cells from a dense input tensor of type %s",
            input_type.to_spec().c_str());
        return {};
    }
    if (modifier_type.count_indexed_dimensions() != 0) {
        LOG(error, "Cannot remove cells using a modifier tensor of type %s",
            modifier_type.to_spec().c_str());
        return {};
    }
    auto input_dim_indexes = calc_mapped_dimension_indexes(input_type, modifier_type);
    if (input_dim_indexes.empty()) {
        LOG(error, "Tensor type mismatch when removing cells from a tensor. "
            "Got input type %s versus modifier type %s",
            input_type.to_spec().c_str(), modifier_type.to_spec().c_str());
        return {};
    }
    SparseCoords addrs(num_mapped_in_input);
    ModifierCoords mod_coords(addrs, input_dim_indexes, modifier_type);
    auto modifier_view = modifier.index().create_view(mod_coords.lookup_view_dims);
    const size_t expected_subspaces = input.index().size();
    const size_t dsss = input_type.dense_subspace_size();
    auto builder = factory.create_value_builder<ICT>(input_type, num_mapped_in_input, dsss, expected_subspaces);
    auto filter_by_modifier = [&] (const auto & lookup_refs, size_t) {
        // The modifier dimensions are setup to point to the input dimensions address storage in ModifierCoords,
        // so we don't need to use the lookup_refs argument.
        (void) lookup_refs;
        modifier_view->lookup(mod_coords.lookup_refs);
        size_t modifier_subspace_index;
        return !(modifier_view->next_result({}, modifier_subspace_index));
    };
    copy_tensor_with_filter<ICT>(input, dsss, addrs, *builder, filter_by_modifier);
    return builder->build(std::move(builder));
}

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
