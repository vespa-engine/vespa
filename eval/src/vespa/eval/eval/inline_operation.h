// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operation.h"
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval::operation {

//-----------------------------------------------------------------------------

struct CallOp1 {
    op1_t my_op1;
    CallOp1(op1_t op1) : my_op1(op1) {}
    double operator()(double a) const { return my_op1(a); }
};

struct TypifyOp1 {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(op1_t value, F &&f) {
        (void) value;
        return f(Result<CallOp1>());
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
template <> struct InlineOp2<Mul> {
    InlineOp2(op2_t) {}
    template <typename A, typename B> constexpr auto operator()(A a, B b) const { return (a*b); }
};

struct TypifyOp2 {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(op2_t value, F &&f) {
        if (value == Add::f) {
            return f(Result<InlineOp2<Add>>());
        } else if (value == Mul::f) {
            return f(Result<InlineOp2<Mul>>());
        } else {
            return f(Result<CallOp2>());
        }
    }
};

//-----------------------------------------------------------------------------

}
