// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stash.h>
#include <memory>
#include <map>
#include "value_type.h"
#include "tensor.h"
#include "tensor_spec.h"
#include "aggr.h"

namespace vespalib {
namespace eval {

struct UnaryOperation;
struct BinaryOperation;

/**
 * A tensor supporting a mix of indexed and mapped dimensions. The
 * goal for this class is to be a simple, complete and correct
 * reference implementation supporting all relevant tensor operations.
 **/
class SimpleTensor : public Tensor
{
public:
    /**
     * A label for a single dimension. This is either a string
     * (mapped) or an integer (indexed). A sequence of Labels form an
     * Address. The labels must have the same order as the dimensions
     * in the tensor type (which are sorted on dimension name). Labels
     * for mapped dimensions must be strings and labels for indexed
     * dimensions must be integers smaller than the dimension size.
     **/
    struct Label {
        size_t index;
        vespalib::string name;
        static constexpr size_t npos = -1;
        Label(const TensorSpec::Label &label)
            : index(label.index), name(label.name) {}
        bool operator<(const Label &rhs) const {
            if (index != rhs.index) {
                return (index < rhs.index);
            }
            return (name < rhs.name);
        }
        bool operator==(const Label &rhs) const {
            return ((index == rhs.index) && (name == rhs.name));
        }
        bool operator!=(const Label &rhs) const { return !(*this == rhs); }
        bool is_mapped() const { return (index == npos); }
        bool is_indexed() const { return (index != npos); }
    };
    using Address = std::vector<Label>;

    /**
     * A tensor has a type and contains a collection of Cells. Each
     * cell has an Address and a value.
     **/
    struct Cell {
        Address address;
        double value;
        Cell(const Address &address_in, double value_in)
            : address(address_in), value(value_in) {}
    };
    using Cells = std::vector<Cell>;

private:
    ValueType _type;
    Cells _cells;

public:
    explicit SimpleTensor(double value);
    SimpleTensor(const ValueType &type_in, Cells &&cells_in);
    const ValueType &type() const { return _type; }
    const Cells &cells() const { return _cells; }
    std::unique_ptr<SimpleTensor> map(const std::function<double(double)> &function) const;
    std::unique_ptr<SimpleTensor> reduce(Aggregator &aggr, const std::vector<vespalib::string> &dimensions) const;
    std::unique_ptr<SimpleTensor> rename(const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to) const;
    static std::unique_ptr<SimpleTensor> create(const TensorSpec &spec);
    static bool equal(const SimpleTensor &a, const SimpleTensor &b);
    static std::unique_ptr<SimpleTensor> join(const SimpleTensor &a, const SimpleTensor &b, const std::function<double(double,double)> &function);
    static std::unique_ptr<SimpleTensor> concat(const SimpleTensor &a, const SimpleTensor &b, const vespalib::string &dimension);
};

} // namespace vespalib::eval
} // namespace vespalib
