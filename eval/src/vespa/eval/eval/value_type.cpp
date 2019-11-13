// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_type.h"
#include "value_type_spec.h"
#include <algorithm>

namespace vespalib::eval {

namespace {

using CellType = ValueType::CellType;
using Dimension = ValueType::Dimension;
using DimensionList = std::vector<Dimension>;

template <typename A, typename B>
CellType unify() {
    using type = typename UnifyCellTypes<A,B>::type;
    return get_cell_type<type>();
}

template <typename A>
CellType unify(CellType b) {
    switch (b) {
    case CellType::DOUBLE: return unify<A,double>();
    case CellType::FLOAT: return unify<A,float>();
    }
    abort();
}

CellType unify(CellType a, CellType b) {
    switch (a) {
    case CellType::DOUBLE: return unify<double>(b);
    case CellType::FLOAT: return unify<float>(b);
    }
    abort();
}

size_t my_dimension_index(const std::vector<Dimension> &list, const vespalib::string &name) {
    for (size_t idx = 0; idx < list.size(); ++idx) {
        if (list[idx].name == name) {
            return idx;
        }
    }
    return ValueType::Dimension::npos;
}

const Dimension *find_dimension(const std::vector<Dimension> &list, const vespalib::string &name) {
    size_t idx = my_dimension_index(list, name);
    return (idx != ValueType::Dimension::npos) ? &list[idx] : nullptr;
}

void sort_dimensions(DimensionList &dimensions) {
    std::sort(dimensions.begin(), dimensions.end(),
              [](const auto &a, const auto &b){ return (a.name < b.name); });
}

bool verify_dimensions(const DimensionList &dimensions) {
    for (size_t i = 0; i < dimensions.size(); ++i) {
        if (dimensions[i].size == 0) {
            return false;
        }
        if ((i > 0) && (dimensions[i - 1].name == dimensions[i].name)) {
            return false;
        }
    }
    return true;
}

struct MyJoin {
    bool mismatch;
    DimensionList dimensions;
    vespalib::string concat_dim;
    MyJoin(const DimensionList &lhs, const DimensionList &rhs)
        : mismatch(false), dimensions(), concat_dim() { my_join(lhs, rhs); }
    MyJoin(const DimensionList &lhs, const DimensionList &rhs, vespalib::string concat_dim_in)
        : mismatch(false), dimensions(), concat_dim(concat_dim_in) { my_join(lhs, rhs); }
    ~MyJoin();
private:
    void add(const Dimension &a) {
        if (a.name == concat_dim) {
            if (a.is_indexed()) {
                dimensions.emplace_back(a.name, a.size + 1);
            } else {
                mismatch = true;
            }
        } else {
            dimensions.push_back(a);
        }
    }
    void unify(const Dimension &a, const Dimension &b) {
        if (a.name == concat_dim) {
            if (a.is_indexed() && b.is_indexed()) {
                dimensions.emplace_back(a.name, a.size + b.size);
            } else {
                mismatch = true;
            }
        } else if (a == b) {
            add(a);
        } else {
            mismatch = true;
        }
    }
    void my_join(const DimensionList &lhs, const DimensionList &rhs) {
        auto pos = rhs.begin();
        auto end = rhs.end();
        for (const Dimension &dim: lhs) {
            while ((pos != end) && (pos->name < dim.name)) {
                add(*pos++);
            }
            if ((pos != end) && (pos->name == dim.name)) {
                unify(dim, *pos++);
            } else {
                add(dim);
            }
        }
        while (pos != end) {
            add(*pos++);
        }
    }
};
MyJoin::~MyJoin() = default;

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
    if (dimensions().empty()) {
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
    if (dimensions().empty()) {
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
ValueType::dense_subspace_size() const
{
    size_t size = 1;
    for (const auto &dim : dimensions()) {
        if (dim.is_indexed()) {
            size *= dim.size;
        }
    }
    return size;
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
    if (is_error()) {
        return error_type();
    } else if (dimensions_in.empty()) {
        return double_type();
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
    return tensor_type(std::move(result), _cell_type);
}

ValueType
ValueType::rename(const std::vector<vespalib::string> &from,
                  const std::vector<vespalib::string> &to) const
{
    if (from.empty() || (from.size() != to.size())) {
        return error_type();
    }
    Renamer renamer(from, to);
    std::vector<Dimension> dim_list;
    for (const auto &dim: _dimensions) {
        dim_list.emplace_back(renamer.rename(dim.name), dim.size);
    }
    if (!renamer.matched_all()) {
        return error_type();
    }
    return tensor_type(dim_list, _cell_type);
}

ValueType
ValueType::tensor_type(std::vector<Dimension> dimensions_in, CellType cell_type)
{
    if (dimensions_in.empty()) {
        return double_type();
    }
    sort_dimensions(dimensions_in);
    if (!verify_dimensions(dimensions_in)) {
        return error_type();
    }
    return ValueType(Type::TENSOR, cell_type, std::move(dimensions_in));
}

ValueType
ValueType::from_spec(const vespalib::string &spec)
{
    return value_type::from_spec(spec);
}

ValueType
ValueType::from_spec(const vespalib::string &spec, std::vector<ValueType::Dimension> &unsorted)
{
    return value_type::from_spec(spec, unsorted);
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
    }
    MyJoin result(lhs._dimensions, rhs._dimensions);
    if (result.mismatch) {
        return error_type();
    }
    return tensor_type(std::move(result.dimensions), unify(lhs._cell_type, rhs._cell_type));
}

CellType
ValueType::unify_cell_types(const ValueType &a, const ValueType &b) {
    if (a.is_double()) {
        return b.cell_type();
    } else if (b.is_double()) {
        return a.cell_type();
    }
    return unify(a.cell_type(), b.cell_type());
}

ValueType
ValueType::concat(const ValueType &lhs, const ValueType &rhs, const vespalib::string &dimension)
{
    if (lhs.is_error() || rhs.is_error()) {
        return error_type();
    }
    MyJoin result(lhs._dimensions, rhs._dimensions, dimension);
    if (result.mismatch) {
        return error_type();
    }
    if (!find_dimension(result.dimensions, dimension)) {
        result.dimensions.emplace_back(dimension, 2);
    }
    return tensor_type(std::move(result.dimensions), unify_cell_types(lhs, rhs));
}

ValueType
ValueType::either(const ValueType &one, const ValueType &other) {
    if (one != other) {
        return error_type();
    }
    return one;
}

std::ostream &
operator<<(std::ostream &os, const ValueType &type) {
    return os << type.to_spec();
}

}
