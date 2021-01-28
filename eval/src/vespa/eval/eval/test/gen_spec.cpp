// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gen_spec.h"
#include <vespa/eval/eval/string_stuff.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

namespace vespalib::eval::test {

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

GenSpec::~GenSpec() = default;

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
