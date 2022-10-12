// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "value_type.h"
#include "value_type_spec.h"
#include <algorithm>
#include <cassert>

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

struct MyReduce {
    bool has_error;
    std::vector<Dimension> dimensions;
    MyReduce(const std::vector<Dimension> &dim_list, const std::vector<vespalib::string> &rem_list)
        : has_error(false), dimensions()
    {
        if (!rem_list.empty()) {
            size_t removed = 0;
            for (const Dimension &dim: dim_list) {
                if (std::find(rem_list.begin(), rem_list.end(), dim.name) == rem_list.end()) {
                    dimensions.push_back(dim);
                } else {
                    ++removed;
                }
            }
            if (removed != rem_list.size()) {
                has_error = true;
            }
        }
    }
};

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

} // namespace vespalib::eval::<unnamed>

constexpr ValueType::Dimension::size_type ValueType::Dimension::npos;

ValueType
ValueType::error_if(bool has_error, ValueType else_type)
{
    if (has_error) {
        return error_type();
    } else {
        return else_type;
    }
}

ValueType::~ValueType() = default;

bool
ValueType::is_double() const {
    if (!_error && _dimensions.empty()) {
        assert(_cell_type == CellType::DOUBLE);
        return true;
    }
    return false;
}

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

bool
ValueType::is_mixed() const
{
    bool seen_mapped = false;
    bool seen_indexed = false;
    for (const auto &dim : dimensions()) {
        seen_mapped |= dim.is_mapped();
        seen_indexed |= dim.is_indexed();
    }
    return (seen_mapped && seen_indexed);
}

size_t
ValueType::count_indexed_dimensions() const
{
    size_t cnt = 0;
    for (const auto &dim : dimensions()) {
        if (dim.is_indexed()) {
            ++cnt;
        }
    }
    return cnt;
}

size_t
ValueType::count_mapped_dimensions() const
{
    size_t cnt = 0;
    for (const auto &dim : dimensions()) {
        if (dim.is_mapped()) {
            ++cnt;
        }
    }
    return cnt;
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

std::vector<ValueType::Dimension>
ValueType::nontrivial_indexed_dimensions() const {
    std::vector<ValueType::Dimension> result;
    for (const auto &dim: dimensions()) {
        if (dim.is_indexed() && !dim.is_trivial()) {
            result.push_back(dim);
        }
    }
    return result;
}

std::vector<ValueType::Dimension>
ValueType::mapped_dimensions() const {
    std::vector<ValueType::Dimension> result;
    for (const auto &dim: dimensions()) {
        if (dim.is_mapped()) {
            result.push_back(dim);
        }
    }
    return result;
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
ValueType::map() const
{
    auto meta = cell_meta().map();
    return error_if(_error, make_type(meta.cell_type, _dimensions));
}

ValueType
ValueType::reduce(const std::vector<vespalib::string> &dimensions_in) const
{
    MyReduce result(_dimensions, dimensions_in);
    auto meta = cell_meta().reduce(result.dimensions.empty());
    return error_if(_error || result.has_error,
                    make_type(meta.cell_type, std::move(result.dimensions)));
}

ValueType
ValueType::peek(const std::vector<vespalib::string> &dimensions_in) const
{
    MyReduce result(_dimensions, dimensions_in);
    auto meta = cell_meta().peek(result.dimensions.empty());
    return error_if(_error || result.has_error || dimensions_in.empty(),
                    make_type(meta.cell_type, std::move(result.dimensions)));
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
    auto meta = cell_meta().rename();
    return error_if(!renamer.matched_all(),
                    make_type(meta.cell_type, std::move(dim_list)));
}

ValueType
ValueType::cell_cast(CellType to_cell_type) const
{
    return error_if(_error, make_type(to_cell_type, _dimensions));
}

ValueType
ValueType::make_type(CellType cell_type, std::vector<Dimension> dimensions_in)
{
    if (dimensions_in.empty() && (cell_type != CellType::DOUBLE)) {
        // Note: all scalar values must have cell_type double
        return error_type();
    }
    sort_dimensions(dimensions_in);
    if (!verify_dimensions(dimensions_in)) {
        return error_type();
    }
    return ValueType(cell_type, std::move(dimensions_in));
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
    MyJoin result(lhs._dimensions, rhs._dimensions);
    auto meta = CellMeta::join(lhs.cell_meta(), rhs.cell_meta());
    return error_if(lhs._error || rhs._error || result.mismatch,
                    make_type(meta.cell_type, std::move(result.dimensions)));
}

ValueType
ValueType::merge(const ValueType &lhs, const ValueType &rhs)
{
    auto meta = CellMeta::merge(lhs.cell_meta(), rhs.cell_meta());
    return error_if(lhs._error || rhs._error || (lhs._dimensions != rhs._dimensions),
                    make_type(meta.cell_type, lhs._dimensions));
}

ValueType
ValueType::concat(const ValueType &lhs, const ValueType &rhs, const vespalib::string &dimension)
{
    MyJoin result(lhs._dimensions, rhs._dimensions, dimension);
    if (!find_dimension(result.dimensions, dimension)) {
        result.dimensions.emplace_back(dimension, 2);
    }
    auto meta = CellMeta::concat(lhs.cell_meta(), rhs.cell_meta());
    return error_if(lhs._error || rhs._error || result.mismatch,
                    make_type(meta.cell_type, std::move(result.dimensions)));
}

ValueType
ValueType::either(const ValueType &one, const ValueType &other) {
    return error_if(one != other, one);
}

std::ostream &
operator<<(std::ostream &os, const ValueType &type) {
    return os << type.to_spec();
}

}
