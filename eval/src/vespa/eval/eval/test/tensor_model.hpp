// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/tensor_engine.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/node_types.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/simple_tensor_engine.h>

namespace vespalib {
namespace eval {
namespace test {

using CellType = ValueType::CellType;
using map_fun_t = TensorEngine::map_fun_t;
using join_fun_t = TensorEngine::join_fun_t;

// Random access sequence of numbers
struct Sequence {
    virtual double operator[](size_t i) const = 0;
    virtual ~Sequence() {}
};

// Sequence of natural numbers (starting at 1)
struct N : Sequence {
    double operator[](size_t i) const override { return (1.0 + i); }
};

// Sequence of another sequence divided by 10
struct Div10 : Sequence {
    const Sequence &seq;
    Div10(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return (seq[i] / 10.0); }
};

// Sequence of another sequence divided by 10
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

// Sequence of applying sigmoid to another sequence
struct Sigmoid : Sequence {
    const Sequence &seq;
    Sigmoid(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return operation::Sigmoid::f(seq[i]); }
};

// Sequence of applying sigmoid to another sequence, plus rounding to nearest float
struct SigmoidF : Sequence {
    const Sequence &seq;
    SigmoidF(const Sequence &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override { return (float)operation::Sigmoid::f(seq[i]); }
};

// pre-defined sequence of numbers
struct Seq : Sequence {
    std::vector<double> seq;
    Seq() : seq() {}
    Seq(const std::vector<double> &seq_in) : seq(seq_in) {}
    double operator[](size_t i) const override {
        ASSERT_LESS(i, seq.size());
        return seq[i];
    }
};

// Random access bit mask
struct Mask {
    virtual bool operator[](size_t i) const = 0;
    virtual ~Mask() {}
};

// Mask with all bits set
struct All : Mask {
    bool operator[](size_t) const override { return true; }
};

// Mask with no bits set
struct None : Mask {
    bool operator[](size_t) const override { return false; }
};

// Mask with false for each Nth index
struct SkipNth : Mask {
    size_t n;
    SkipNth(size_t n_in) : n(n_in) {}
    bool operator[](size_t i) const override { return (i % n) != 0; }
};

// pre-defined mask
struct Bits : Mask {
    std::vector<bool> bits;
    Bits(const std::vector<bool> &bits_in) : bits(bits_in) {}
    ~Bits() { }
    bool operator[](size_t i) const override {
        ASSERT_LESS(i, bits.size());
        return bits[i];
    }
};

// A mask converted to a sequence of two unique values (mapped from true and false)
struct Mask2Seq : Sequence {
    const Mask &mask;
    double true_value;
    double false_value;
    Mask2Seq(const Mask &mask_in, double true_value_in = 1.0, double false_value_in = 0.0)
        : mask(mask_in), true_value(true_value_in), false_value(false_value_in) {}
    double operator[](size_t i) const override { return mask[i] ? true_value : false_value; }
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
Domain::Domain(const Domain &) = default;
Domain::~Domain() {}

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

Layout float_cells(const Layout &layout) {
    return Layout(CellType::FLOAT, layout.domains);
}

Domain x() { return Domain("x", {}); }
Domain x(size_t size) { return Domain("x", size); }
Domain x(const std::vector<vespalib::string> &keys) { return Domain("x", keys); }

Domain y() { return Domain("y", {}); }
Domain y(size_t size) { return Domain("y", size); }
Domain y(const std::vector<vespalib::string> &keys) { return Domain("y", keys); }

Domain z() { return Domain("z", {}); }
Domain z(size_t size) { return Domain("z", size); }
Domain z(const std::vector<vespalib::string> &keys) { return Domain("z", keys); }

// Infer the tensor type spanned by the given spaces
vespalib::string infer_type(const Layout &layout) {
    std::vector<ValueType::Dimension> dimensions;
    for (const auto &domain: layout) {
        if (domain.size == 0) {
            dimensions.emplace_back(domain.dimension); // mapped
        } else {
            dimensions.emplace_back(domain.dimension, domain.size); // indexed
        }
    }
    return ValueType::tensor_type(dimensions, layout.cell_type).to_spec();
}

// Wrapper for the things needed to generate a tensor
struct Source {
    using Address = TensorSpec::Address;

    const Layout   &layout;
    const Sequence &seq;
    const Mask     &mask;
    Source(const Layout &layout_in, const Sequence &seq_in, const Mask &mask_in)
        : layout(layout_in), seq(seq_in), mask(mask_in) {}
};

// Mix layout with a number sequence to make a tensor spec
class TensorSpecBuilder
{
private:
    using Label = TensorSpec::Label;
    using Address = TensorSpec::Address;

    Source     _source;
    TensorSpec _spec;
    Address    _addr;
    size_t     _idx;

    void generate(size_t layout_idx) {
        if (layout_idx == _source.layout.size()) {
            if (_source.mask[_idx]) {
                _spec.add(_addr, _source.seq[_idx]);
            }
            ++_idx;
        } else {
            const Domain &domain = _source.layout[layout_idx];
            if (domain.size > 0) { // indexed
                for (size_t i = 0; i < domain.size; ++i) {
                    _addr.emplace(domain.dimension, Label(i)).first->second = Label(i);
                    generate(layout_idx + 1);
                }
            } else { // mapped
                for (const vespalib::string &key: domain.keys) {
                    _addr.emplace(domain.dimension, Label(key)).first->second = Label(key);
                    generate(layout_idx + 1);
                }
            }
        }
    }

public:
    TensorSpecBuilder(const Layout &layout, const Sequence &seq, const Mask &mask)
        : _source(layout, seq, mask), _spec(infer_type(layout)), _addr(), _idx(0) {}
    TensorSpec build() {
        generate(0);
        return _spec;
    }
};
TensorSpec spec(const Layout &layout, const Sequence &seq, const Mask &mask) {
    return TensorSpecBuilder(layout, seq, mask).build();
}
TensorSpec spec(const Layout &layout, const Sequence &seq) {
    return spec(layout, seq, All());
}
TensorSpec spec(const Layout &layout) {
    return spec(layout, Seq(), None());
}
TensorSpec spec(const Domain &domain, const Sequence &seq, const Mask &mask) {
    return spec(Layout({domain}), seq, mask);
}
TensorSpec spec(const Domain &domain, const Sequence &seq) {
    return spec(Layout({domain}), seq);
}
TensorSpec spec(const Domain &domain) {
    return spec(Layout({domain}));
}
TensorSpec spec(double value) {
    return spec(Layout({}), Seq({value}));
}
TensorSpec spec() {
    return spec(Layout({}));
}

TensorSpec spec(const vespalib::string &type,
                const std::vector<std::pair<TensorSpec::Address, TensorSpec::Value>> &cells) {
    TensorSpec spec("tensor(" + type + ")");

    for (const auto &cell : cells) {
        spec.add(cell.first, cell.second);
    }
    return spec;
}

TensorSpec spec(const vespalib::string &value_expr) {
    if (value_expr == "error") {
        return TensorSpec("error");
    }
    const auto &engine = SimpleTensorEngine::ref();
    auto fun = Function::parse(value_expr);
    ASSERT_TRUE(!fun->has_error());
    ASSERT_EQUAL(fun->num_params(), 0u);
    NodeTypes types(*fun, {});
    ASSERT_TRUE(!types.get_type(fun->root()).is_error());
    InterpretedFunction ifun(engine, *fun, types);
    InterpretedFunction::Context ctx(ifun);
    SimpleObjectParams params({});
    auto result = engine.to_spec(ifun.eval(ctx, params));
    ASSERT_TRUE(!result.cells().empty());
    return result;
}

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
