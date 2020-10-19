
#include "wrapped_simple_value.h"
#include "cell_values.h"
#include "tensor_address_builder.h"
#include "tensor_visitor.h"
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/instruction/generic_concat.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/instruction/generic_merge.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/instruction/generic_rename.h>
#include <vespa/eval/tensor/default_value_builder_factory.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.wrapped_simple_value");

using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueBuilderFactory;
using namespace vespalib::eval::instruction;

namespace vespalib::tensor {

namespace {

TensorSpec::Address
sparsify_address(const TensorSpec::Address &address)
{
    TensorSpec::Address result;
    for (const auto &elem : address) {
        if (elem.second.is_indexed()) {
            auto val = vespalib::make_string("%zu", elem.second.index);
            result.emplace(elem.first, TensorSpec::Label(val));
        } else {
            result.emplace(elem);
        }
    }
    return result;
}

TensorSpec::Address
extract_sparse_address(const TensorSpec::Address &address)
{
    TensorSpec::Address result;
    for (const auto &elem : address) {
        if (elem.second.is_mapped()) {
            result.emplace(elem);
        }
    }
    return result;
}

Tensor::UP
maybe_wrap(Value::UP value)
{
    Tensor::UP retval;
    if (auto tensor = dynamic_cast<Tensor *>(value.get())) {
        retval.reset(tensor);
        value.release();
        return retval;
    }
    return std::make_unique<WrappedSimpleValue>(std::move(value));
}

struct PerformGenericApply {
    template <typename CT>
    static auto invoke(const Value &input, const CellFunction &func,
                       const ValueBuilderFactory &factory)
    {
        const auto &type = input.type();
        size_t subspace_size = type.dense_subspace_size();
        size_t num_mapped = type.count_mapped_dimensions();
        auto builder = factory.create_value_builder<CT>(type, num_mapped, subspace_size, input.index().size());
        auto input_cells = input.cells().typify<CT>();
        auto view = input.index().create_view({});
        std::vector<vespalib::stringref> output_address(num_mapped);
        std::vector<vespalib::stringref *> input_address;
        for (auto & label : output_address) {
            input_address.push_back(&label);
        }
        view->lookup({});
        size_t subspace;
        while (view->next_result(input_address, subspace)) {
            auto dst = builder->add_subspace(output_address);
            size_t input_offset = subspace_size * subspace;
            for (size_t i = 0; i < subspace_size; ++i) {
                dst[i] = func.apply(input_cells[input_offset + i]);
            }
        }
        return builder->build(std::move(builder));
    }
};

} // namespace <unnamed>


eval::TensorSpec
WrappedSimpleValue::toSpec() const
{
    return spec_from_value(_tensor);
}

void
WrappedSimpleValue::accept(TensorVisitor &visitor) const
{
    TensorSpec myspec = toSpec();
    TensorAddressBuilder addr;
    for (const auto & cell : myspec.cells()) {
        auto sparse_addr = sparsify_address(cell.first);
        addr.clear();
        for (const auto & dim_and_label : sparse_addr) {
            addr.add(dim_and_label.first, dim_and_label.second.name);
        }
        visitor.visit(addr.build(), cell.second);
    }
}

MemoryUsage
WrappedSimpleValue::get_memory_usage() const
{
    size_t used = sizeof(WrappedSimpleValue);
    if (_space) {
        auto plus = _space->get_memory_usage();
        plus.incUsedBytes(used);
        plus.incAllocatedBytes(used);
        return plus;
    }
    return MemoryUsage(used, used, 0, 0);
}

//-----------------------------------------------------------------------------

Tensor::UP
WrappedSimpleValue::apply(const CellFunction & func) const
{
    auto up = typify_invoke<1,eval::TypifyCellType,PerformGenericApply>(
            _tensor.type().cell_type(),
            _tensor, func, DefaultValueBuilderFactory::get());
    return maybe_wrap(std::move(up));
}

Tensor::UP
WrappedSimpleValue::join(join_fun_t fun, const Tensor &rhs) const
{
    Tensor::UP retval;
    auto up = GenericJoin::perform_join(*this, rhs, fun, DefaultValueBuilderFactory::get());
    return maybe_wrap(std::move(up));
}

Tensor::UP
WrappedSimpleValue::merge(join_fun_t fun, const Tensor &rhs) const
{
    Tensor::UP retval;
    auto up = GenericMerge::perform_merge(*this, rhs, fun, DefaultValueBuilderFactory::get());
    return maybe_wrap(std::move(up));
}

Tensor::UP
WrappedSimpleValue::reduce(eval::Aggr aggr, const std::vector<vespalib::string> & dimensions) const
{
    auto up = GenericReduce::perform_reduce(*this, aggr, dimensions, DefaultValueBuilderFactory::get());
    return maybe_wrap(std::move(up));
}

Tensor::UP
WrappedSimpleValue::reduce(join_fun_t fun, const std::vector<vespalib::string> &dims) const
{
    if (fun == eval::operation::Mul::f) {
        return reduce(eval::Aggr::PROD, dims);
    }
    if (fun == eval::operation::Add::f) {
        return reduce(eval::Aggr::SUM, dims);
    }
    if (fun == eval::operation::Max::f) {
        return reduce(eval::Aggr::MAX, dims);
    }
    if (fun == eval::operation::Min::f) {
        return reduce(eval::Aggr::MIN, dims);
    }
    // unknown join-expressible reduce operation
    abort();
}

Tensor::UP
WrappedSimpleValue::modify(join_fun_t fun, const CellValues &cellValues) const
{
    TensorSpec a = toSpec();
    TensorSpec b = cellValues.toSpec();
    TensorSpec result(a.type());
    auto end_iter = b.cells().end();
    for (const auto &cell: a.cells()) {
        double v = cell.second;
        auto sparse_addr = sparsify_address(cell.first);
        auto iter = b.cells().find(sparse_addr);
        if (iter == end_iter) {
            result.add(cell.first, v);
        } else {
            result.add(cell.first, fun(v, iter->second));
        }
    }
    return maybe_wrap(value_from_spec(result, DefaultValueBuilderFactory::get()));
}

Tensor::UP
WrappedSimpleValue::add(const Tensor &rhs) const
{
    TensorSpec a = toSpec();
    TensorSpec b = rhs.toSpec();
    if (a.type() != b.type()) {
        return {};
    }
    TensorSpec result(a.type());
    for (const auto &cell: b.cells()) {
        result.add(cell.first, cell.second);
    }
    auto end_iter = b.cells().end();
    for (const auto &cell: a.cells()) {
        auto iter = b.cells().find(cell.first);
        if (iter == end_iter) {
            result.add(cell.first, cell.second);
        }
    }
    return maybe_wrap(value_from_spec(result, DefaultValueBuilderFactory::get()));
}


Tensor::UP
WrappedSimpleValue::remove(const CellValues &rhs) const
{
    TensorSpec a = toSpec();
    TensorSpec b = rhs.toSpec();
    TensorSpec result(a.type());
    auto end_iter = b.cells().end();
    for (const auto &cell: a.cells()) {
        TensorSpec::Address mappedAddress = extract_sparse_address(cell.first);
        auto iter = b.cells().find(mappedAddress);
        if (iter == end_iter) {
            result.add(cell.first, cell.second);
        }
    }
    return maybe_wrap(value_from_spec(result, DefaultValueBuilderFactory::get()));
}

Tensor::UP
WrappedSimpleValue::concat(const Value &b, const vespalib::string &dimension) const
{
    auto up = GenericConcat::perform_concat(*this, b, dimension, DefaultValueBuilderFactory::get());
    return maybe_wrap(std::move(up));
}

Tensor::UP
WrappedSimpleValue::rename(const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to) const
{
    auto up = GenericRename::perform_rename(*this, from, to, DefaultValueBuilderFactory::get());
    return maybe_wrap(std::move(up));
}

}
