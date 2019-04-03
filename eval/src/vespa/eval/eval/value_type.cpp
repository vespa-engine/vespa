// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_type.h"
#include "value_type_spec.h"
#include <algorithm>

namespace vespalib::eval {

namespace {

using Dimension = ValueType::Dimension;
using DimensionList = std::vector<Dimension>;

size_t my_dimension_index(const std::vector<Dimension> &list, const vespalib::string &name) {
    for (size_t idx = 0; idx < list.size(); ++idx) {
        if (list[idx].name == name) {
            return idx;
        }
    }
    return ValueType::Dimension::npos;
}

Dimension *find_dimension(std::vector<Dimension> &list, const vespalib::string &name) {
    size_t idx = my_dimension_index(list, name);
    return (idx != ValueType::Dimension::npos) ? &list[idx] : nullptr;
}

const Dimension *find_dimension(const std::vector<Dimension> &list, const vespalib::string &name) {
    size_t idx = my_dimension_index(list, name);
    return (idx != ValueType::Dimension::npos) ? &list[idx] : nullptr;
}

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

struct Renamer {
    const std::vector<vespalib::string> &from;
    const std::vector<vespalib::string> &to;
    size_t match_cnt;
    Renamer(const std::vector<vespalib::string> &from_in,
            const std::vector<vespalib::string> &to_in)
        : from(from_in), to(to_in), match_cnt(0) {}
    const vespalib::string &rename(const vespalib::string &name) {
        for (size_t i = 0; i < from.size(); ++i) {
            if (name == from[i]) {
                ++match_cnt;
                return to[i];
            }
        }
        return name;
    }
    bool matched_all() const { return (match_cnt == from.size()); }
};

} // namespace vespalib::tensor::<unnamed>

constexpr ValueType::Dimension::size_type ValueType::Dimension::npos;

ValueType::~ValueType() = default;
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

size_t
ValueType::dimension_index(const vespalib::string &name) const {
    return my_dimension_index(_dimensions, name);
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
ValueType::reduce(const std::vector<vespalib::string> &dimensions_in) const
{
    if (is_error() || is_any()) {
        return *this;
    } else if (dimensions_in.empty()) {
        return double_type();
    } else if (!is_tensor()) {
        return error_type();
    } else if (_dimensions.empty()) {
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
        return double_type();
    }
    return tensor_type(std::move(result));
}

ValueType
ValueType::rename(const std::vector<vespalib::string> &from,
                  const std::vector<vespalib::string> &to) const
{
    if (!maybe_tensor() || from.empty() || (from.size() != to.size())) {
        return error_type();
    }
    if (unknown_dimensions()) {
        return any_type();
    }
    Renamer renamer(from, to);
    std::vector<Dimension> dim_list;
    for (const auto &dim: _dimensions) {
        dim_list.emplace_back(renamer.rename(dim.name), dim.size);
    }
    if (!renamer.matched_all()) {
        return error_type();
    }
    return tensor_type(dim_list);
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
    } else if (lhs.is_double()) {
        return rhs;
    } else if (rhs.is_double()) {
        return lhs;
    } else if (lhs.unknown_dimensions() || rhs.unknown_dimensions()) {
        return any_type();
    }
    DimensionResult result = my_join(lhs._dimensions, rhs._dimensions);
    if (result.mismatch) {
        return error_type();
    }
    return tensor_type(std::move(result.dimensions));
}

ValueType
ValueType::concat(const ValueType &lhs, const ValueType &rhs, const vespalib::string &dimension)
{
    if (lhs.is_error() || rhs.is_error()) {
        return error_type();
    } else if (lhs.unknown_dimensions() || rhs.unknown_dimensions()) {
        return any_type();
    }
    DimensionResult result = my_join(lhs._dimensions, rhs._dimensions);
    auto lhs_dim = find_dimension(lhs.dimensions(), dimension);
    auto rhs_dim = find_dimension(rhs.dimensions(), dimension);
    auto res_dim = find_dimension(result.dimensions, dimension);
    if (result.mismatch || (res_dim && res_dim->is_mapped())) {
        return error_type();
    }
    if (res_dim) {
        if (res_dim->is_bound()) {
            res_dim->size = (lhs_dim ? lhs_dim->size : 1)
                            + (rhs_dim ? rhs_dim->size : 1);
        }
    } else {
        result.dimensions.emplace_back(dimension, 2);
    }
    return tensor_type(std::move(result.dimensions));
}

ValueType
ValueType::either(const ValueType &one, const ValueType &other)
{
    if (one.is_error() || other.is_error()) {
        return error_type();
    }
    if (one == other) {
        return one;
    }
    if (!one.is_tensor() || !other.is_tensor()) {
        return any_type();
    }
    if (one.dimensions().size() != other.dimensions().size()) {
        return tensor_type({});
    }
    std::vector<Dimension> dims;
    for (size_t i = 0; i < one.dimensions().size(); ++i) {
        const Dimension &a = one.dimensions()[i];
        const Dimension &b = other.dimensions()[i];
        if (a.name != b.name) {
            return tensor_type({});
        }
        if (a.is_mapped() != b.is_mapped()) {
            return tensor_type({});
        }
        if (a.size == b.size) {
            dims.push_back(a);
        } else {
            dims.emplace_back(a.name, 0);
        }
    }
    return tensor_type(std::move(dims));
}

std::ostream &
operator<<(std::ostream &os, const ValueType &type) {
    return os << type.to_spec();
}

}
