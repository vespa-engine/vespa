// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/approx.h>
#include <ostream>
#include <chrono>

namespace std::chrono {
//TODO Move to a more suitable place
template <typename rep, typename period>
ostream & operator << (ostream & os, duration<rep, period> ts) {
    return os << ts.count();
}

ostream & operator << (ostream & os, system_clock::time_point ts);
ostream & operator << (ostream & os, steady_clock::time_point ts);

}

namespace vespalib {

/**
 * Collection of generic test comparators.
 **/
class TestComparators
{
private:
    TestComparators() {}
public:
    /** @brief Functor implementing approximately equals (with an epsilon) */
    struct approx {
        double _eps;
        approx(double eps) : _eps(eps) {}
        template <class A, class B>
        bool operator()(const A &a, const B &b) const {
            if (a < b) {
                return (b - a) <= _eps;
            } else {
                return (a - b) <= _eps;
            }
        }
    };
    /** @brief Functor implementing not approximately equals (with an epsilon) */
    struct not_approx {
        approx _approx;
        not_approx(double eps) : _approx(eps) {}
        template <class A, class B>
        bool operator()(const A &a, const B &b) const {
            return !_approx(a, b);
        }
    };
    /** @brief Functor implementing equals */
    struct equal {
        template <class A, class B>
        bool operator()(const A &a, const B &b) const { return (a == b); }
        bool operator()(double a, double b) const { return approx_equal(a, b); }
    };
    /** @brief Functor implementing not-equals */
    struct not_equal {
        template <class A, class B>
        bool operator()(const A &a, const B &b) const { return !equal()(a, b); }
    };
    /** @brief Functor implementing is-less-than */
    struct less {
        template <class A, class B>
        bool operator()(const A &a, const B &b) const { return (a < b); }
    };
    /** @brief Functor implementing is-less-than-or-equal */
    struct less_equal {
        template <class A, class B>
        bool operator()(const A &a, const B &b) const { return less()(a, b) || equal()(a, b); }
    };
    /** @brief Functor implementing is-greater-than */
    struct greater {
        template <class A, class B>
        bool operator()(const A &a, const B &b) const { return less()(b, a); }
    };
    /** @brief Functor implementing is-greater-than-or-equal */
    struct greater_equal {
        template <class A, class B>
        bool operator()(const A &a, const B &b) const { return greater()(a, b) || equal()(a, b); }
    };
};

} // namespace vespalib

