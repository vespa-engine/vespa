// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/eval/value_type.h>
#include <vector>

namespace vespalib {
namespace tensor {

/**
 * The type of 'a single tensor' or 'the result of a tensor
 * operation'.
 *
 * TensorType is a value class containing implementation-independent
 * type information for a single tensor or the result of a tensor
 * operation. The type of the result of a tensor operation depends
 * only on the types of the input values and the type of the operation
 * itself. Illegal operations will result in the type INVALID. For the
 * collapsing operation 'sum', the result will be of type NUMBER. Both
 * SPARSE and DENSE tensor types will contain information about their
 * dimensions. Dimension 'size' is only relevant for DENSE tensors and
 * will be set to 'npos' for SPARSE tensors.
 **/
class TensorType
{
public:
    enum class Type { INVALID, NUMBER, SPARSE, DENSE };
    struct Dimension {
        static constexpr size_t npos = -1;
        vespalib::string name;
        size_t size;
        explicit Dimension(const vespalib::string &name_in)
            : name(name_in), size(npos) {}
        Dimension(const vespalib::string &name_in, size_t size_in)
            : name(name_in), size(size_in) {}
        bool operator==(const Dimension &rhs) const {
            return ((name == rhs.name) && (size == rhs.size));
        }
        bool operator!=(const Dimension &rhs) const { return !(*this == rhs); }
    };

private:
    Type _type;
    std::vector<Dimension> _dimensions;

    explicit TensorType(Type type_in) // INVALID/NUMBER
        : _type(type_in), _dimensions() {}
    TensorType(Type type_in, std::vector<Dimension> &&dimensions_in) // SPARSE/DENSE
        : _type(type_in), _dimensions(std::move(dimensions_in)) {}

public:
    Type type() const { return _type; }
    bool is_valid() const { return (_type != Type::INVALID); }
    bool is_number() const { return (_type == Type::NUMBER); }
    bool is_tensor() const {
        return ((_type == Type::SPARSE) || (_type == Type::DENSE));
    }
    const std::vector<Dimension> &dimensions() const { return _dimensions; }

    bool operator==(const TensorType &rhs) const {
        if ((_type == Type::INVALID) || (rhs._type == Type::INVALID)) {
            return false;
        }
        return ((_type == rhs._type) && (_dimensions == rhs._dimensions));
    }
    bool operator!=(const TensorType &rhs) const { return !(*this == rhs); }

    TensorType remove_dimensions(const std::vector<vespalib::string> &dimensions_in) const;
    TensorType add_dimensions_from(const TensorType &rhs) const;
    TensorType keep_dimensions_in(const TensorType &rhs) const;

    eval::ValueType as_value_type() const;

    static TensorType invalid();
    static TensorType number();
    static TensorType sparse(const std::vector<vespalib::string> &dimensions_in);
    static TensorType dense(std::vector<Dimension> dimensions_in);
    static TensorType fromSpec(const vespalib::string &str);
    vespalib::string toSpec() const;
};

std::ostream &operator<<(std::ostream &os, const TensorType &type);

} // namespace vespalib::tensor
} // namespace vespalib
