// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gen_spec.h"
#include <vespa/eval/eval/string_stuff.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace vespalib::eval::test {

//-----------------------------------------------------------------------------

Sequence N(double bias) {
    return [bias](size_t i) noexcept { return (i + bias); };
}

Sequence AX_B(double a, double b) {
    return [a,b](size_t i) noexcept { return (a * i) + b; };
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
    return [seq](size_t i) noexcept { return seq[i % seq.size()]; };
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

// 'a2' -> DimSpec("a", 2);
// 'b2_3' -> DimSpec("b", make_dict(2, 3, ""));
DimSpec
DimSpec::from_desc(const vespalib::string &desc)
{
    size_t idx = 0;
    vespalib::string name;
    auto is_num = [](char c) { return ((c >= '0') && (c <= '9')); };
    auto as_num = [](char c) { return size_t(c - '0'); };
    auto is_map_tag = [](char c) { return (c == '_'); };
    auto is_dim_name = [](char c) { return ((c >= 'a') && (c <= 'z')); };
    auto extract_number = [&]() {
        assert(idx < desc.size());
        assert(is_num(desc[idx]));
        size_t num = as_num(desc[idx++]);
        assert(num != 0); // catch leading zeroes/zero size
        while ((idx < desc.size()) && is_num(desc[idx])) {
            num = (num * 10) + as_num(desc[idx++]);
        }
        return num;
    };
    assert(!desc.empty());
    assert(is_dim_name(desc[idx]));
    name.push_back(desc[idx++]);
    size_t size = extract_number();
    if (idx < desc.size()) {
        // mapped
        assert(is_map_tag(desc[idx++]));
        size_t stride = extract_number();
        assert(idx == desc.size());
        return {name, make_dict(size, stride, "")};
    } else {
        // indexed
        return {name, size};
    }
}

// 'a2b12c5' -> GenSpec().idx("a", 2).idx("b", 12).idx("c", 5);
// 'a2_1b3_2c5_1' -> GenSpec().map("a", 2).map("b", 3, 2).map("c", 5);
GenSpec
GenSpec::from_desc(const vespalib::string &desc)
{
    size_t idx = 0;
    vespalib::string dim_desc;
    std::vector<DimSpec> dim_list;
    auto is_dim_name = [](char c) { return ((c >= 'a') && (c <= 'z')); };
    while (idx < desc.size()) {
        dim_desc.clear();
        assert(is_dim_name(desc[idx]));
        dim_desc.push_back(desc[idx++]);
        while ((idx < desc.size()) && !is_dim_name(desc[idx])) {
            dim_desc.push_back(desc[idx++]);
        }
        dim_list.push_back(DimSpec::from_desc(dim_desc));
    }
    return {std::move(dim_list)};
}

GenSpec::GenSpec(GenSpec &&other) = default;
GenSpec::GenSpec(const GenSpec &other) = default;
GenSpec &GenSpec::operator=(GenSpec &&other) = default;
GenSpec &GenSpec::operator=(const GenSpec &other) = default;

GenSpec::~GenSpec() = default;

bool
GenSpec::bad_scalar() const
{
    return (_dims.empty() && (_cells != CellType::DOUBLE));
}

ValueType
GenSpec::type() const
{
    std::vector<ValueType::Dimension> dim_types;
    for (const auto &dim: _dims) {
        dim_types.push_back(dim.type());
    }
    auto type = ValueType::make_type(_cells, dim_types);
    assert(!type.is_error());
    return type;
}

TensorSpec
GenSpec::gen() const
{
    size_t idx = 0;
    TensorSpec::Address addr;   
    assert(!bad_scalar());
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
