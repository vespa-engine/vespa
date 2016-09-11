// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/stringfmt.h>
#include "tensor_type.h"
#include <algorithm>
#include <ostream>
#include "tensor_type_spec.h"

using vespalib::eval::ValueType;

namespace vespalib {
namespace tensor {

namespace {

void sort_dimensions(std::vector<TensorType::Dimension> &dimensions) {
    std::sort(dimensions.begin(), dimensions.end(),
              [](const auto &a, const auto &b){ return (a.name < b.name); });
}

bool has_duplicates(const std::vector<TensorType::Dimension> &dimensions) {
    for (size_t i = 1; i < dimensions.size(); ++i) {
        if (dimensions[i - 1].name == dimensions[i].name) {
            return true;
        }
    }
    return false;
}

struct DimensionMatcher {
    bool mismatch;
    const std::vector<TensorType::Dimension> &dimension_list;
    explicit DimensionMatcher(const std::vector<TensorType::Dimension> &dimension_list_in)
        : mismatch(false), dimension_list(dimension_list_in) {}
    bool find(const TensorType::Dimension &dimension) {
        for (const auto &d: dimension_list) {
            if (dimension.name == d.name) {
                if (dimension.size != d.size) {
                    mismatch = true;
                }
                return true;
            }
        }
        return false;
    }
};

} // namespace vespalib::tensor::<unnamed>

constexpr size_t TensorType::Dimension::npos;

TensorType
TensorType::remove_dimensions(const std::vector<vespalib::string> &dimensions_in) const
{
    if (!is_tensor()) {
        return invalid();
    }
    size_t removed = 0;
    std::vector<Dimension> result;
    for (const Dimension &d: _dimensions) {
        if (std::find(dimensions_in.begin(), dimensions_in.end(), d.name) == dimensions_in.end()) {
            result.push_back(d);
        } else {
            ++removed;
        }
    }
    if (removed != dimensions_in.size()) {
        return invalid();
    }
    return TensorType(_type, std::move(result));
}

TensorType
TensorType::add_dimensions_from(const TensorType &rhs) const
{
    if (!is_tensor() || (_type != rhs._type)) {
        return invalid();
    }
    std::vector<Dimension> result(_dimensions);
    DimensionMatcher matcher(result);
    for (const Dimension &d: rhs._dimensions) {
        if (!matcher.find(d)) {
            result.push_back(d);
        }
    }
    if (matcher.mismatch) {
        return invalid();
    }
    sort_dimensions(result);
    return TensorType(_type, std::move(result));
}

TensorType
TensorType::keep_dimensions_in(const TensorType &rhs) const
{
    if (!is_tensor() || (_type != rhs._type)) {
        return invalid();
    }
    std::vector<Dimension> result;
    DimensionMatcher matcher(rhs._dimensions);
    for (const Dimension &d: _dimensions) {
        if (matcher.find(d)) {
            result.push_back(d);
        }
    }
    if (matcher.mismatch) {
        return invalid();
    }
    return TensorType(_type, std::move(result));
}

ValueType
TensorType::as_value_type() const
{
    if (is_number() || (is_tensor() && dimensions().empty())) {
        return ValueType::double_type();
    }
    if (is_tensor()) {
        std::vector<ValueType::Dimension> my_dimensions;
        for (const auto &dimension: dimensions()) {
            my_dimensions.emplace_back(dimension.name, dimension.size);
        }
        return ValueType::tensor_type(std::move(my_dimensions));
    }
    return ValueType::error_type();
}

TensorType
TensorType::sparse(const std::vector<vespalib::string> &dimensions_in)
{
    std::vector<Dimension> dimensions;
    for (const auto &dimension_name: dimensions_in) {
        dimensions.emplace_back(dimension_name);
    }
    sort_dimensions(dimensions);
    if (has_duplicates(dimensions)) {
        return invalid();
    }
    return TensorType(Type::SPARSE, std::move(dimensions));
}

TensorType
TensorType::dense(std::vector<Dimension> dimensions_in)
{
    sort_dimensions(dimensions_in);
    if (has_duplicates(dimensions_in)) {
        return invalid();
    }
    return TensorType(Type::DENSE, std::move(dimensions_in));
}

std::ostream &operator<<(std::ostream &os, const TensorType &type) {
    size_t cnt = 0;
    switch (type.type()) {
    case TensorType::Type::INVALID:
        os << "INVALID";
        break;
    case TensorType::Type::NUMBER:
        os << "NUMBER";
        break;
    case TensorType::Type::SPARSE:
        os << "SPARSE(";
        for (const auto &d: type.dimensions()) {
            if (cnt++ > 0) {
                os << ",";
            }
            os << d.name;
        }
        os << ")";
        break;
    case TensorType::Type::DENSE:
        os << "DENSE(";
        for (const auto &d: type.dimensions()) {
            if (cnt++ > 0) {
                os << ",";
            }
            os << vespalib::make_string("{%s:%zu}", d.name.c_str(), d.size);
        }
        os << ")";
        break;
    }
    return os;
}


TensorType
TensorType::fromSpec(const vespalib::string &str)
{
    return tensor_type::fromSpec(str);
}


vespalib::string
TensorType::toSpec() const
{
    return tensor_type::toSpec(*this);
}


} // namespace vespalib::tensor
} // namespace vespalib
