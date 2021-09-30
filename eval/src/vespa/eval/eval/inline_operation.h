// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operation.h"
#include <vespa/vespalib/util/typify.h>
#include <cmath>

namespace vespalib::eval::operation {

//-----------------------------------------------------------------------------

struct CallOp1 {
    op1_t my_op1;
    CallOp1(op1_t op1) : my_op1(op1) {}
    double operator()(double a) const { return my_op1(a); }
};

template <typename T> struct InlineOp1;
template <> struct InlineOp1<Cube> {
    InlineOp1(op1_t) {}
    template <typename A> constexpr auto operator()(A a) const { return (a * a * a); }
};
template <> struct InlineOp1<Exp> {
    InlineOp1(op1_t) {}
    template <typename A> constexpr auto operator()(A a) const { return exp(a); }
};
template <> struct InlineOp1<Inv> {
    InlineOp1(op1_t) {}
    template <typename A> constexpr auto operator()(A a) const { return (A{1}/a); }
};
template <> struct InlineOp1<Sqrt> {
    InlineOp1(op1_t) {}
    template <typename A> constexpr auto operator()(A a) const { return std::sqrt(a); }
};
template <> struct InlineOp1<Square> {
    InlineOp1(op1_t) {}
    template <typename A> constexpr auto operator()(A a) const { return (a * a); }
};
template <> struct InlineOp1<Tanh> {
    InlineOp1(op1_t) {}
    template <typename A> constexpr auto operator()(A a) const { return std::tanh(a); }
};

struct TypifyOp1 {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(op1_t value, F &&f) {
        if (value == Cube::f) {
            return f(Result<InlineOp1<Cube>>());
        } else if (value == Exp::f) {
            return f(Result<InlineOp1<Exp>>());
        } else if (value == Inv::f) {
            return f(Result<InlineOp1<Inv>>());
        } else if (value == Sqrt::f) {
            return f(Result<InlineOp1<Sqrt>>());
        } else if (value == Square::f) {
            return f(Result<InlineOp1<Square>>());
        } else if (value == Tanh::f) {
            return f(Result<InlineOp1<Tanh>>());
        } else {
            return f(Result<CallOp1>());
        }
    }
};

//-----------------------------------------------------------------------------

struct CallOp2 {
    op2_t my_op2;
    CallOp2(op2_t op2) : my_op2(op2) {}
    op2_t get() const { return my_op2; }
    double operator()(double a, double b) const { return my_op2(a, b); }
};

template <typename Op2>
struct SwapArgs2 {
    Op2 op2;
    SwapArgs2(op2_t op2_in) : op2(op2_in) {}
    template <typename A, typename B> constexpr auto operator()(A a, B b) const { return op2(b, a); }
};

template <typename T> struct InlineOp2;
template <> struct InlineOp2<Add> {
    InlineOp2(op2_t) {}
    template <typename A, typename B> constexpr auto operator()(A a, B b) const { return (a+b); }
};
template <> struct InlineOp2<Div> {
    InlineOp2(op2_t) {}
    template <typename A, typename B> constexpr auto operator()(A a, B b) const { return (a/b); }
};
template <> struct InlineOp2<Mul> {
    InlineOp2(op2_t) {}
    template <typename A, typename B> constexpr auto operator()(A a, B b) const { return (a*b); }
};
template <> struct InlineOp2<Pow> {
    InlineOp2(op2_t) {}
    constexpr float operator()(float a, float b) const { return std::pow(a,b); }
    constexpr double operator()(float a, double b) const { return std::pow(a,b); }
    constexpr double operator()(double a, float b) const { return std::pow(a,b); }
    constexpr double operator()(double a, double b) const { return std::pow(a,b); }
};
template <> struct InlineOp2<Sub> {
    InlineOp2(op2_t) {}
    template <typename A, typename B> constexpr auto operator()(A a, B b) const { return (a-b); }
};

struct TypifyOp2 {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(op2_t value, F &&f) {
        if (value == Add::f) {
            return f(Result<InlineOp2<Add>>());
        } else if (value == Div::f) {
            return f(Result<InlineOp2<Div>>());
        } else if (value == Mul::f) {
            return f(Result<InlineOp2<Mul>>());
        } else if (value == Pow::f) {
            return f(Result<InlineOp2<Pow>>());
        } else if (value == Sub::f) {
            return f(Result<InlineOp2<Sub>>());
        } else {
            return f(Result<CallOp2>());
        }
    }
};

//-----------------------------------------------------------------------------

template <typename A, typename OP1>
void apply_op1_vec(A *dst, const A *src, size_t n, OP1 &&f) {
    for (size_t i = 0; i < n; ++i) {
        dst[i] = f(src[i]);
    }
}

template <typename D, typename A, typename B, typename OP2>
void apply_op2_vec_num(D *dst, const A *a, B b, size_t n, OP2 &&f) {
    for (size_t i = 0; i < n; ++i) {
        dst[i] = f(a[i], b);
    }
}

template <typename D, typename A, typename B, typename OP2>
void apply_op2_vec_vec(D *dst, const A *a, const B *b, size_t n, OP2 &&f) {
    for (size_t i = 0; i < n; ++i) {
        dst[i] = f(a[i], b[i]);
    }
}

//-----------------------------------------------------------------------------

}
