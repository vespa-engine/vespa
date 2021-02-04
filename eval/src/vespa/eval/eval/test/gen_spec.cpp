// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gen_spec.h"
#include <vespa/eval/eval/string_stuff.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace vespalib::eval::test {

//-----------------------------------------------------------------------------

Sequence N(double bias) {
    return [bias](size_t i) { return (i + bias); };
}

Sequence AX_B(double a, double b) {
    return [a,b](size_t i) { return (a * i) + b; };
}

Sequence Div16(const Sequence &seq) {
    return [seq](size_t i) { return (seq(i) / 16.0); };
}

Sequence Sub2(const Sequence &seq) {
    return [seq](size_t i) { return (seq(i) - 2.0); };
}

Sequence OpSeq(const Sequence &seq, map_fun_t op) {
    return [seq,op](size_t i) { return op(seq(i)); };
}

Sequence SigmoidF(const Sequence &seq) {
    return [seq](size_t i) { return (float)operation::Sigmoid::f(seq(i)); };
}

Sequence Seq(const std::vector<double> &seq) {
    assert(!seq.empty());
    return [seq](size_t i) { return seq[i % seq.size()]; };
}

//-----------------------------------------------------------------------------

DimSpec::~DimSpec() = default;

std::vector<vespalib::string>
DimSpec::make_dict(size_t size, size_t stride, const vespalib::string &prefix)
{
    std::vector<vespalib::string> dict;
    for (size_t i = 0; i < size; ++i) {
        dict.push_back(fmt("%s%zu", prefix.c_str(), i * stride));
    }
    return dict;
}

GenSpec::GenSpec(GenSpec &&other) = default;
GenSpec::GenSpec(const GenSpec &other) = default;
GenSpec &GenSpec::operator=(GenSpec &&other) = default;
GenSpec &GenSpec::operator=(const GenSpec &other) = default;

GenSpec::~GenSpec() = default;

ValueType
GenSpec::type() const
{
    std::vector<ValueType::Dimension> dim_types;
    for (const auto &dim: _dims) {
        dim_types.push_back(dim.type());
    }
    auto type = ValueType::tensor_type(dim_types, _cells);
    assert(!type.is_error());
    return type;
}

TensorSpec
GenSpec::gen() const
{
    size_t idx = 0;
    TensorSpec::Address addr;   
    TensorSpec result(type().to_spec());
    std::function<void(size_t)> add_cells = [&](size_t dim_idx) {
        if (dim_idx == _dims.size()) {
            result.add(addr, _seq(idx++));
        } else {
            const auto &dim = _dims[dim_idx];
            for (size_t i = 0; i < dim.size(); ++i) {
                addr.insert_or_assign(dim.name(), dim.label(i));
                add_cells(dim_idx + 1);
            }
        }
    };
    add_cells(0);
    return result;
}

} // namespace
