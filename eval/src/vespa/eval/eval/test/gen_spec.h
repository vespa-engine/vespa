// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/operation.h>
#include <functional>
#include <cassert>

namespace vespalib::eval::test {

using map_fun_t = vespalib::eval::operation::op1_t;
using join_fun_t = vespalib::eval::operation::op2_t;
using Sequence = std::function<double(size_t)>;

// Sequence counting up from 1 (default)
// bias (starting point) can be adjusted
// bias = 1.5 -> 1.5, 2.5, 3.5 ...
Sequence N(double bias = 1.0);

// Sequence of numbers ax + b (where x is the index)
Sequence AX_B(double a, double b);

// Sequence of another sequence divided by 16
Sequence Div16(const Sequence &seq);

// Sequence of another sequence divided by 17
Sequence Div17(const Sequence &seq);

// Sequence of another sequence minus 2
Sequence Sub2(const Sequence &seq);

// Sequence of a unary operator applied to a sequence
Sequence OpSeq(const Sequence &seq, map_fun_t op);

// Sequence of applying sigmoid to another sequence, plus rounding to nearest float
Sequence SigmoidF(const Sequence &seq);

// pre-defined repeating sequence of numbers
Sequence Seq(const std::vector<double> &seq);

/**
 * Type and labels for a single dimension of a TensorSpec to be
 * generated. Dimensions are specified independent of each other for
 * simplicity. All dense subspaces will be padded during conversion to
 * actual values, which means that indexed dimensions are inherently
 * independent already. Using different labels for the same mapped
 * dimension for different tensors should enable us to exhibit
 * sufficient levels of partial overlap.
 **/
class DimSpec
{
private:
    vespalib::string              _name;
    size_t                        _size;
    std::vector<vespalib::string> _dict;
public:
    DimSpec(const vespalib::string &name, size_t size) noexcept;
    DimSpec(const vespalib::string &name, std::vector<vespalib::string> dict) noexcept;
    DimSpec(DimSpec &&) noexcept;
    DimSpec & operator=(DimSpec &&) noexcept;
    DimSpec & operator=(const DimSpec &);
    DimSpec(const DimSpec &);
    ~DimSpec();
    static std::vector<vespalib::string> make_dict(size_t size, size_t stride, const vespalib::string &prefix);
    ValueType::Dimension type() const {
        return _size ? ValueType::Dimension{_name, uint32_t(_size)} : ValueType::Dimension{_name};
    }
    const vespalib::string &name() const { return _name; }
    size_t size() const {
        return _size ? _size : _dict.size();
    }
    TensorSpec::Label label(size_t idx) const {
        assert(idx < size());
        return _size ? TensorSpec::Label{idx} : TensorSpec::Label{_dict[idx]};
    }

    // Make a DimSpec object from a textual description
    // (first character is used as dimension name)
    //
    // 'a2' -> DimSpec("a", 2);
    // 'b2_3' -> DimSpec("b", make_dict(2, 3, ""));
    static DimSpec from_desc(const vespalib::string &desc);
};

/**
 * Specification defining how to generate a TensorSpec. Typically used
 * to generate complex values for testing and benchmarking.
 **/
class GenSpec
{
public:
    using seq_t = Sequence;

private:
    std::vector<DimSpec> _dims;
    CellType _cells;
    seq_t _seq;

public:
    GenSpec() noexcept : _dims(), _cells(CellType::DOUBLE), _seq(N()) {}
    GenSpec(double bias) noexcept : _dims(), _cells(CellType::DOUBLE), _seq(N(bias)) {}
    GenSpec(std::vector<DimSpec> dims_in) noexcept : _dims(std::move(dims_in)), _cells(CellType::DOUBLE), _seq(N()) {}

    // Make a GenSpec object from a textual description
    // (dimension names must be single characters a-z)
    //
    // 'a2b12c5' -> GenSpec().idx("a", 2).idx("b", 12).idx("c", 5);
    // 'a2_1b3_2c5_1' -> GenSpec().map("a", 2).map("b", 3, 2).map("c", 5);
    static GenSpec from_desc(const vespalib::string &desc);

    GenSpec(GenSpec &&other) noexcept;
    GenSpec(const GenSpec &other);
    GenSpec &operator=(GenSpec &&other) noexcept;
    GenSpec &operator=(const GenSpec &other);
    ~GenSpec();
    const std::vector<DimSpec> &dims() const { return _dims; }
    CellType cells() const { return _cells; }
    const seq_t &seq() const { return _seq; }
    GenSpec cpy() const { return *this; }
    GenSpec &idx(const vespalib::string &name, size_t size) {
        assert(size != 0);
        _dims.emplace_back(name, size);
        return *this;
    }
    GenSpec &map(const vespalib::string &name, size_t size, size_t stride = 1, const vespalib::string &prefix = "") {
        _dims.emplace_back(name, DimSpec::make_dict(size, stride, prefix));
        return *this;
    }
    GenSpec &map(const vespalib::string &name, std::vector<vespalib::string> dict) {
        _dims.emplace_back(name, std::move(dict));
        return *this;
    }
    GenSpec &desc(const vespalib::string &dim_desc) {
        _dims.push_back(DimSpec::from_desc(dim_desc));
        return *this;
    }
    GenSpec &cells(CellType cell_type) {
        _cells = cell_type;
        return *this;
    }
    GenSpec &cells_double() { return cells(CellType::DOUBLE); }
    GenSpec &cells_float() { return cells(CellType::FLOAT); }
    GenSpec &seq(const seq_t &seq_in) {
        _seq = seq_in;
        return *this;
    }
    GenSpec &seq(const std::vector<double> &numbers) { return seq(Seq({numbers})); }
    bool bad_scalar() const;
    ValueType type() const;
    TensorSpec gen() const;
    operator TensorSpec() const { return gen(); }
};

std::ostream &operator<<(std::ostream &out, const GenSpec &spec);

} // namespace
