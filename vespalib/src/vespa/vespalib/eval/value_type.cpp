// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "value_type.h"
#include "value_type_spec.h"

namespace vespalib {
namespace eval {

namespace {

using Dimension = ValueType::Dimension;
using DimensionList = std::vector<Dimension>;

void sort_dimensions(DimensionList &dimensions) {
    std::sort(dimensions.begin(), dimensions.end(),
              [](const auto &a, const auto &b){ return (a.name < b.name); });
}

bool has_duplicates(const DimensionList &dimensions) {
    for (size_t i = 1; i < dimensions.size(); ++i) {
        if (dimensions[i - 1].name == dimensions[i].name) {
            return true;
        }
    }
    return false;
}

struct DimensionResult {
    bool mismatch;
    DimensionList dimensions;
    DimensionResult() : mismatch(false), dimensions() {}
    void add(const Dimension &a) {
        dimensions.push_back(a);
    }
    void unify(const Dimension &a, const Dimension &b) {
        if (a.is_mapped() == b.is_mapped()) {
            add(Dimension(a.name, std::min(a.size, b.size)));
        } else {
            mismatch = true;
        }
    }
};

DimensionResult my_join(const DimensionList &lhs, const DimensionList &rhs) {
    DimensionResult result;
    auto pos = rhs.begin();
    auto end = rhs.end();
    for (const Dimension &dim: lhs) {
        while ((pos != end) && (pos->name < dim.name)) {
            result.add(*pos++);
        }
        if ((pos != end) && (pos->name == dim.name)) {
            result.unify(dim, *pos++);
        } else {
            result.add(dim);
        }
    }
    while (pos != end) {
        result.add(*pos++);
    }
    return result;
}

DimensionResult my_intersect(const DimensionList &lhs, const DimensionList &rhs) {
    DimensionResult result;
    auto pos = rhs.begin();
    auto end = rhs.end();
    for (const Dimension &dim: lhs) {
        while ((pos != end) && (pos->name < dim.name)) {
            ++pos;
        }
        if ((pos != end) && (pos->name == dim.name)) {
            result.unify(dim, *pos++);
        }
    }
    return result;
}

} // namespace vespalib::tensor::<unnamed>

constexpr size_t ValueType::Dimension::npos;

bool
ValueType::is_sparse() const
{
    if (!is_tensor() || dimensions().empty()) {
        return false;
    }
    for (const auto &dim : dimensions()) {
        if (!dim.is_mapped()) {
            return false;
        }
    }
    return true;
}

bool
ValueType::is_dense() const
{
    if (!is_tensor() || dimensions().empty()) {
        return false;
    }
    for (const auto &dim : dimensions()) {
        if (!dim.is_indexed()) {
            return false;
        }
    }
    return true;
}

std::vector<vespalib::string>
ValueType::dimension_names() const
{
    std::vector<vespalib::string> result;
    for (const auto &dimension: _dimensions) {
        result.push_back(dimension.name);
    }
    return result;
}

ValueType
ValueType::remove_dimensions(const std::vector<vespalib::string> &dimensions_in) const
{
    if (!maybe_tensor() || dimensions_in.empty()) {
        return error_type();
    }
    if (unknown_dimensions()) {
        return any_type();
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
        return error_type();
    }
    if (result.empty()) {
        return ValueType::double_type();
    }
    return ValueType(_type, std::move(result));
}

ValueType
ValueType::add_dimensions_from(const ValueType &rhs) const
{
    if (!maybe_tensor() || !rhs.maybe_tensor()) {
        return error_type();
    }
    if (unknown_dimensions() || rhs.unknown_dimensions()) {
        return any_type();
    }
    DimensionResult result = my_join(_dimensions, rhs._dimensions);
    if (result.mismatch) {
        return error_type();
    }
    return ValueType(_type, std::move(result.dimensions));
}

ValueType
ValueType::keep_dimensions_in(const ValueType &rhs) const
{
    if (!maybe_tensor() || !rhs.maybe_tensor()) {
        return error_type();
    }
    if (unknown_dimensions() || rhs.unknown_dimensions()) {
        return any_type();
    }
    DimensionResult result = my_intersect(_dimensions, rhs._dimensions);
    if (result.mismatch) {
        return error_type();
    }
    return ValueType(_type, std::move(result.dimensions));
}

ValueType
ValueType::tensor_type(std::vector<Dimension> dimensions_in)
{
    sort_dimensions(dimensions_in);
    if (has_duplicates(dimensions_in)) {
        return error_type();
    }
    return ValueType(Type::TENSOR, std::move(dimensions_in));
}

ValueType
ValueType::from_spec(const vespalib::string &spec)
{
    return value_type::from_spec(spec);
}

vespalib::string
ValueType::to_spec() const
{
    return value_type::to_spec(*this);
}

ValueType
ValueType::join(const ValueType &lhs, const ValueType &rhs)
{
    if (lhs.is_error() || rhs.is_error()) {
        return error_type();
    } else if (lhs.is_any() || rhs.is_any()) {
        return any_type();
    } else if (lhs.is_double()) {
        return rhs;
    } else if (rhs.is_double()) {
        return lhs;
    }
    return lhs.add_dimensions_from(rhs);
}

std::ostream &
operator<<(std::ostream &os, const ValueType &type) {
    return os << type.to_spec();
}

} // namespace vespalib::eval
} // namespace vespalib
