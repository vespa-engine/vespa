// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "cell_type.h"
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace vespalib::eval {

/**
 * The type of a Value. This is used for type-resolution during
 * compilation of interpreted functions using boxed polymorphic
 * values.
 **/
class ValueType
{
public:
    struct Dimension {
        using size_type = uint32_t;
        static constexpr size_type npos = -1;
        vespalib::string name;
        size_type size;
        Dimension(const vespalib::string &name_in) noexcept
            : name(name_in), size(npos) {}
        Dimension(const vespalib::string &name_in, size_type size_in) noexcept
            : name(name_in), size(size_in) {}
        bool operator==(const Dimension &rhs) const {
            return ((name == rhs.name) && (size == rhs.size));
        }
        bool operator!=(const Dimension &rhs) const { return !(*this == rhs); }
        bool is_mapped() const { return (size == npos); }
        bool is_indexed() const { return (size != npos); }
        bool is_trivial() const { return (size == 1); }
    };

private:
    bool                   _error;
    CellType               _cell_type;
    std::vector<Dimension> _dimensions;

    ValueType()
        : _error(true), _cell_type(CellType::DOUBLE), _dimensions() {}

    ValueType(CellType cell_type_in, std::vector<Dimension> &&dimensions_in)
        : _error(false), _cell_type(cell_type_in), _dimensions(std::move(dimensions_in)) {}

    static ValueType error_if(bool has_error, ValueType else_type);

public:
    ValueType(ValueType &&) noexcept = default;
    ValueType(const ValueType &) = default;
    ValueType &operator=(ValueType &&) noexcept = default;
    ValueType &operator=(const ValueType &) = default;
    ~ValueType();
    CellType cell_type() const { return _cell_type; }
    CellMeta cell_meta() const { return {_cell_type, is_double()}; }
    bool is_error() const { return _error; }
    bool is_double() const;
    bool has_dimensions() const { return !_dimensions.empty(); }
    bool is_sparse() const;
    bool is_dense() const;
    size_t count_indexed_dimensions() const;
    size_t count_mapped_dimensions() const;
    size_t dense_subspace_size() const;
    const std::vector<Dimension> &dimensions() const { return _dimensions; }
    std::vector<Dimension> nontrivial_indexed_dimensions() const;
    std::vector<Dimension> mapped_dimensions() const;
    size_t dimension_index(const vespalib::string &name) const;
    std::vector<vespalib::string> dimension_names() const;
    bool operator==(const ValueType &rhs) const {
        return ((_error == rhs._error) &&
                (_cell_type == rhs._cell_type) &&
                (_dimensions == rhs._dimensions));
    }
    bool operator!=(const ValueType &rhs) const { return !(*this == rhs); }

    ValueType map() const;
    ValueType reduce(const std::vector<vespalib::string> &dimensions_in) const;
    ValueType peek(const std::vector<vespalib::string> &dimensions_in) const;
    ValueType rename(const std::vector<vespalib::string> &from,
                     const std::vector<vespalib::string> &to) const;
    ValueType cell_cast(CellType to_cell_type) const;

    static ValueType error_type() { return ValueType(); }
    static ValueType make_type(CellType cell_type, std::vector<Dimension> dimensions_in);
    static ValueType double_type() { return make_type(CellType::DOUBLE, {}); }
    static ValueType from_spec(const vespalib::string &spec);
    static ValueType from_spec(const vespalib::string &spec, std::vector<ValueType::Dimension> &unsorted);
    vespalib::string to_spec() const;
    static ValueType join(const ValueType &lhs, const ValueType &rhs);
    static ValueType merge(const ValueType &lhs, const ValueType &rhs);
    static ValueType concat(const ValueType &lhs, const ValueType &rhs, const vespalib::string &dimension);
    static ValueType either(const ValueType &one, const ValueType &other);
};

std::ostream &operator<<(std::ostream &os, const ValueType &type);

} // namespace
