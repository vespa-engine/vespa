// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
    enum class Type { ERROR, DOUBLE, TENSOR };
    enum class CellType : char { FLOAT, DOUBLE };
    struct Dimension {
        using size_type = uint32_t;
        static constexpr size_type npos = -1;
        vespalib::string name;
        size_type size;
        Dimension(const vespalib::string &name_in)
            : name(name_in), size(npos) {}
        Dimension(const vespalib::string &name_in, size_type size_in)
            : name(name_in), size(size_in) {}
        bool operator==(const Dimension &rhs) const {
            return ((name == rhs.name) && (size == rhs.size));
        }
        bool operator!=(const Dimension &rhs) const { return !(*this == rhs); }
        bool is_mapped() const { return (size == npos); }
        bool is_indexed() const { return (size != npos); }
    };

private:
    Type                   _type;
    CellType               _cell_type;
    std::vector<Dimension> _dimensions;

    ValueType(Type type_in)
        : _type(type_in), _cell_type(CellType::DOUBLE), _dimensions() {}

    ValueType(Type type_in, CellType cell_type_in, std::vector<Dimension> &&dimensions_in)
        : _type(type_in), _cell_type(cell_type_in), _dimensions(std::move(dimensions_in)) {}

public:
    ValueType(ValueType &&) = default;
    ValueType(const ValueType &) = default;
    ValueType &operator=(ValueType &&) = default;
    ValueType &operator=(const ValueType &) = default;
    ~ValueType();
    Type type() const { return _type; }
    CellType cell_type() const { return _cell_type; }
    bool is_error() const { return (_type == Type::ERROR); }
    bool is_double() const { return (_type == Type::DOUBLE); }
    bool is_tensor() const { return (_type == Type::TENSOR); }
    bool is_sparse() const;
    bool is_dense() const;
    size_t dense_subspace_size() const;
    const std::vector<Dimension> &dimensions() const { return _dimensions; }
    size_t dimension_index(const vespalib::string &name) const;
    std::vector<vespalib::string> dimension_names() const;
    bool operator==(const ValueType &rhs) const {
        return ((_type == rhs._type) &&
                (_cell_type == rhs._cell_type) &&
                (_dimensions == rhs._dimensions));
    }
    bool operator!=(const ValueType &rhs) const { return !(*this == rhs); }

    ValueType reduce(const std::vector<vespalib::string> &dimensions_in) const;
    ValueType rename(const std::vector<vespalib::string> &from,
                     const std::vector<vespalib::string> &to) const;

    static ValueType error_type() { return ValueType(Type::ERROR); }
    static ValueType double_type() { return ValueType(Type::DOUBLE); }
    static ValueType tensor_type(std::vector<Dimension> dimensions_in, CellType cell_type = CellType::DOUBLE);
    static ValueType from_spec(const vespalib::string &spec);
    static ValueType from_spec(const vespalib::string &spec, std::vector<ValueType::Dimension> &unsorted);
    vespalib::string to_spec() const;
    static ValueType join(const ValueType &lhs, const ValueType &rhs);
    static CellType unify_cell_types(const ValueType &a, const ValueType &b);
    static ValueType concat(const ValueType &lhs, const ValueType &rhs, const vespalib::string &dimension);
    static ValueType either(const ValueType &one, const ValueType &other);
};

std::ostream &operator<<(std::ostream &os, const ValueType &type);

// utility templates

template <typename CT> inline bool check_cell_type(ValueType::CellType type);
template <> inline bool check_cell_type<double>(ValueType::CellType type) { return (type == ValueType::CellType::DOUBLE); }
template <> inline bool check_cell_type<float>(ValueType::CellType type) { return (type == ValueType::CellType::FLOAT); }

template <typename LCT, typename RCT> struct UnifyCellTypes{};
template <> struct UnifyCellTypes<double, double> { using type = double; };
template <> struct UnifyCellTypes<double, float>  { using type = double; };
template <> struct UnifyCellTypes<float,  double> { using type = double; };
template <> struct UnifyCellTypes<float,  float>  { using type = float; };

template <typename CT> inline ValueType::CellType get_cell_type();
template <> inline ValueType::CellType get_cell_type<double>() { return ValueType::CellType::DOUBLE; }
template <> inline ValueType::CellType get_cell_type<float>() { return ValueType::CellType::FLOAT; }

} // namespace
