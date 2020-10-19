// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "wrapped_simple_value.h"
#include "cell_values.h"
#include "tensor_address_builder.h"
#include "tensor_visitor.h"
#include <vespa/eval/eval/memory_usage_stuff.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.tensor.wrapped_simple_value");

using vespalib::eval::TensorSpec;
using vespalib::eval::SimpleValueBuilderFactory;

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

Tensor::UP wrap(eval::Value::UP value) {
    return std::make_unique<WrappedSimpleValue>(std::move(value));
}

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
    MemoryUsage rv = eval::self_memory_usage<WrappedSimpleValue>();
    if (_space) {
        rv.merge(_space->get_memory_usage());
    }
    return rv;
}

//-----------------------------------------------------------------------------

Tensor::UP
WrappedSimpleValue::apply(const CellFunction &) const
{
    LOG_ABORT("should not be reached");
}

Tensor::UP
WrappedSimpleValue::join(join_fun_t, const Tensor &) const
{
    LOG_ABORT("should not be reached");
}

Tensor::UP
WrappedSimpleValue::merge(join_fun_t, const Tensor &) const
{
    LOG_ABORT("should not be reached");
}

Tensor::UP
WrappedSimpleValue::reduce(join_fun_t, const std::vector<vespalib::string> &) const
{
    LOG_ABORT("should not be reached");
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
    return wrap(value_from_spec(result, SimpleValueBuilderFactory::get()));
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
    return wrap(value_from_spec(result, SimpleValueBuilderFactory::get()));
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
    return wrap(value_from_spec(result, SimpleValueBuilderFactory::get()));
}

}
