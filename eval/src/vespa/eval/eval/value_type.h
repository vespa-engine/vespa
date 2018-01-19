// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory>

namespace vespalib::eval {

/**
 * The type of a Value. This is used for type-resolution during
 * compilation of interpreted functions using boxed polymorphic
 * values.
 **/
class ValueType
{
public:
    enum class Type { ANY, ERROR, DOUBLE, TENSOR };
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
        bool is_bound() const { return ((size != npos) && (size != 0)); }
    };

private:
    Type _type;
    std::vector<Dimension> _dimensions;

    explicit ValueType(Type type_in)
        : _type(type_in), _dimensions() {}
    ValueType(Type type_in, std::vector<Dimension> &&dimensions_in)
        : _type(type_in), _dimensions(std::move(dimensions_in)) {}

public:
    ValueType(ValueType &&) = default;
    ValueType(const ValueType &) = default;
    ValueType &operator=(ValueType &&) = default;
    ValueType &operator=(const ValueType &) = default;
    ~ValueType();
    Type type() const { return _type; }
    bool is_any() const { return (_type == Type::ANY); }
    bool is_error() const { return (_type == Type::ERROR); }
    bool is_double() const { return (_type == Type::DOUBLE); }
    bool is_tensor() const { return (_type == Type::TENSOR); }
    bool is_sparse() const;
    bool is_dense() const;
    const std::vector<Dimension> &dimensions() const { return _dimensions; }
    size_t dimension_index(const vespalib::string &name) const;
    std::vector<vespalib::string> dimension_names() const;
    bool maybe_tensor() const { return (is_any() || is_tensor()); }
    bool unknown_dimensions() const { return (maybe_tensor() && _dimensions.empty()); }
    bool is_abstract() const {
        for (const auto &dimension: _dimensions) {
            if (dimension.is_indexed() && !dimension.is_bound()) {
                return true;
            }
        }
        return (is_any() || (is_tensor() && (dimensions().empty())));
    }
    bool operator==(const ValueType &rhs) const {
        return ((_type == rhs._type) && (_dimensions == rhs._dimensions));
    }
    bool operator!=(const ValueType &rhs) const { return !(*this == rhs); }

    ValueType reduce(const std::vector<vespalib::string> &dimensions_in) const;
    ValueType rename(const std::vector<vespalib::string> &from,
                     const std::vector<vespalib::string> &to) const;

    static ValueType any_type() { return ValueType(Type::ANY); }
    static ValueType error_type() { return ValueType(Type::ERROR); };
    static ValueType double_type() { return ValueType(Type::DOUBLE); }
    static ValueType tensor_type(std::vector<Dimension> dimensions_in);
    static ValueType from_spec(const vespalib::string &spec);
    vespalib::string to_spec() const;
    static ValueType join(const ValueType &lhs, const ValueType &rhs);
    static ValueType concat(const ValueType &lhs, const ValueType &rhs, const vespalib::string &dimension);
    static ValueType either(const ValueType &one, const ValueType &other);
};

std::ostream &operator<<(std::ostream &os, const ValueType &type);

}
