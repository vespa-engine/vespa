// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/cell_type.h>
#include <cassert>
#include <vector>

namespace vespalib::eval::test {

using map_fun_t = vespalib::eval::operation::op1_t;
using join_fun_t = vespalib::eval::operation::op2_t;

// Random access sequence of numbers
struct Sequence {
    virtual double operator[](size_t i) const = 0;
    virtual ~Sequence() {}
};

// Sequence of natural numbers (starting at 1)
struct N : Sequence {
    double operator[](size_t i) const override { return (1.0 + i); }
};

// Sequence of another sequence divided by 16
struct Div16 : Sequence {
    const Sequence &seq;
    Div16(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return (seq[i] / 16.0); }
};

// Sequence of another sequence minus 2
struct Sub2 : Sequence {
    const Sequence &seq;
    Sub2(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return (seq[i] - 2.0); }
};

// Sequence of a unary operator applied to a sequence
struct OpSeq : Sequence {
    const Sequence &seq;
    map_fun_t op;
    OpSeq(const Sequence &seq_in, map_fun_t op_in) : seq(seq_in), op(op_in) {}
    double operator[](size_t i) const override { return op(seq[i]); }
};

// Sequence of applying sigmoid to another sequence, plus rounding to nearest float
struct SigmoidF : Sequence {
    const Sequence &seq;
    SigmoidF(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return (float)operation::Sigmoid::f(seq[i]); }
};

// pre-defined repeating sequence of numbers
struct Seq : Sequence {
    std::vector<double> seq;
    Seq() : seq() {}
    Seq(const std::vector<double> &seq_in)
        : seq(seq_in) { assert(!seq_in.empty()); }
    ~Seq() override;
    double operator[](size_t i) const override {
        return seq[i % seq.size()];
    }
};

// custom op1
struct MyOp {
    static double f(double a) {
        return ((a + 1) * 2);
    }
};

// 'a in [1,5,7,13,42]'
struct MyIn {
    static double f(double a) {
        if ((a ==  1) ||
            (a ==  5) ||
            (a ==  7) ||
            (a == 13) ||
            (a == 42))
        {
            return 1.0;
        } else {
            return 0.0;
        }
    }
};

// A collection of labels for a single dimension
struct Domain {
    vespalib::string dimension;
    size_t size; // indexed
    std::vector<vespalib::string> keys; // mapped
    Domain(const Domain &);
    Domain(const vespalib::string &dimension_in, size_t size_in)
        : dimension(dimension_in), size(size_in), keys() {}
    Domain(const vespalib::string &dimension_in, const std::vector<vespalib::string> &keys_in)
        : dimension(dimension_in), size(0), keys(keys_in) {}
    ~Domain();
};

struct Layout {
    CellType cell_type;
    std::vector<Domain> domains;
    Layout(std::initializer_list<Domain> domains_in)
        : cell_type(CellType::DOUBLE), domains(domains_in) {}
    Layout(CellType cell_type_in, std::vector<Domain> domains_in)
        : cell_type(cell_type_in), domains(std::move(domains_in)) {}
    auto begin() const { return domains.begin(); }
    auto end() const { return domains.end(); }
    auto size() const { return domains.size(); }
    auto operator[](size_t idx) const { return domains[idx]; }
};

Layout float_cells(const Layout &layout);

Domain x();
Domain x(size_t size);
Domain x(const std::vector<vespalib::string> &keys);

Domain y();
Domain y(size_t size);
Domain y(const std::vector<vespalib::string> &keys);

Domain z();
Domain z(size_t size);
Domain z(const std::vector<vespalib::string> &keys);

// Infer the tensor type spanned by the given spaces
vespalib::string infer_type(const Layout &layout);

TensorSpec spec(const Layout &layout, const Sequence &seq);
TensorSpec spec(const Domain &domain, const Sequence &seq);
TensorSpec spec(double value);
TensorSpec spec(const vespalib::string &type,
                const std::vector<std::pair<TensorSpec::Address, TensorSpec::Value>> &cells);
TensorSpec spec(const vespalib::string &value_expr);

}
