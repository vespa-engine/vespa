// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <functional>
#include <cassert>

namespace vespalib::eval::test {

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
    DimSpec(const vespalib::string &name, size_t size) noexcept
        : _name(name), _size(size), _dict()
    {
        assert(_size);
    }
    DimSpec(const vespalib::string &name, std::vector<vespalib::string> dict) noexcept
        : _name(name), _size(), _dict(std::move(dict))
    {
        assert(!_size);
    }
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
};

/**
 * Specification defining how to generate a TensorSpec. Typically used
 * to generate complex values for testing and benchmarking.
 **/
class GenSpec
{
public:
    using seq_t = std::function<double(size_t)>;
private:
    std::vector<DimSpec> _dims;
    CellType _cells;
    seq_t _seq;

    static double default_seq(size_t idx) { return (idx + 1.0); }
public:
    GenSpec() : _dims(), _cells(CellType::DOUBLE), _seq(default_seq) {}
    ~GenSpec();
    std::vector<DimSpec> dims() const { return _dims; }
    CellType cells() const { return _cells; }
    seq_t seq() const { return _seq; }
    GenSpec &idx(const vespalib::string &name, size_t size) {
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
    GenSpec &cells(CellType cell_type) {
        _cells = cell_type;
        return *this;
    }
    GenSpec &cells_double() { return cells(CellType::DOUBLE); }
    GenSpec &cells_float() { return cells(CellType::FLOAT); }
    GenSpec &seq(seq_t seq_in) {
        _seq = seq_in;
        return *this;
    }
    GenSpec &seq_n() { return seq(default_seq); }
    GenSpec &seq_bias(double bias) {
        seq_t fun = [bias](size_t idx) { return (idx + bias); };
        return seq(fun);
    }
    ValueType type() const;
    TensorSpec gen() const;
};

} // namespace
