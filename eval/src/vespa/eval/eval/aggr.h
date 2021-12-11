// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/stllike/string.h>
#include <limits>
#include <vector>
#include <map>
#include <algorithm>
#include <cmath>

namespace vespalib { class Stash; }

namespace vespalib::eval {


/**
 * Enumeration of all different aggregators that are allowed to be
 * used in tensor reduce expressions.
 **/
enum class Aggr { AVG, COUNT, PROD, SUM, MAX, MEDIAN, MIN };

/**
 * Utiliy class used to map between aggregator enum value and symbolic
 * name. For example Aggr::AVG <-> "avg".
 **/
class AggrNames {
private:
    static const AggrNames _instance;
    std::map<vespalib::string,Aggr> _name_aggr_map;
    std::map<Aggr,vespalib::string> _aggr_name_map;
    void add(Aggr aggr, const vespalib::string &name);
    AggrNames();
public:
    static const vespalib::string *name_of(Aggr aggr);
    static const Aggr *from_name(const vespalib::string &name);
};

/**
 * Interface defining a general purpose aggregator that can be re-used
 * to aggregate multiple groups of values. Each number group is
 * aggregated by calling 'first' once, followed by any number of calls
 * to 'next', before finally calling 'result' to obtain the
 * aggregation result. The 'create' function acts as a factory able to
 * create Aggregator instances for all known aggregator enum values
 * defined above.
 **/
struct Aggregator {
    virtual void first(double value) = 0;
    virtual void next(double value) = 0;
    virtual double result() const = 0;
    virtual Aggr enum_value() const = 0;
    virtual ~Aggregator();
    static Aggregator &create(Aggr aggr, Stash &stash);
    static std::vector<Aggr> list();
};

namespace aggr {

// can we start by picking any value from the set to be reduced (or
// the special aggregator-specific null_value) and use the templated
// aggregator 'combine' function in arbitrary order to end up with
// (approximately) the correct result?
constexpr bool is_simple(Aggr aggr) {
    return ((aggr == Aggr::PROD) ||
            (aggr == Aggr::SUM)  ||
            (aggr == Aggr::MAX)  ||
            (aggr == Aggr::MIN));
}

// will a single value reduce to itself?
constexpr bool is_ident(Aggr aggr) {
    return ((aggr == Aggr::AVG)    ||
            (aggr == Aggr::PROD)   ||
            (aggr == Aggr::SUM)    ||
            (aggr == Aggr::MAX)    ||
            (aggr == Aggr::MEDIAN) ||
            (aggr == Aggr::MIN));
}

// should we avoid doing clever stuff with this aggregator?
constexpr bool is_complex(Aggr aggr) {
    return (aggr == Aggr::MEDIAN);
}

template <typename T> class Avg {
private:
    T _sum;
    size_t _cnt;
public:
    using value_type = T;
    constexpr Avg() noexcept : _sum{0}, _cnt{0} {}
    constexpr Avg(T value) : _sum{value}, _cnt{1} {}
    constexpr void sample(T value) {
        _sum += value;
        ++_cnt;
    }
    constexpr void merge(const Avg &rhs) {
        _sum += rhs._sum;
        _cnt += rhs._cnt;
    };
    constexpr T result() const { return (_sum / _cnt); }
    static constexpr Aggr enum_value() { return Aggr::AVG; }
};

template <typename T> class Count {
private:
    size_t _cnt;
public:
    using value_type = T;
    constexpr Count() noexcept : _cnt{0} {}
    constexpr Count(T) : _cnt{1} {}
    constexpr void sample(T) { ++_cnt; }
    constexpr void merge(const Count &rhs) { _cnt += rhs._cnt; }
    constexpr T result() const { return _cnt; }
    static constexpr Aggr enum_value() { return Aggr::COUNT; }
};

template <typename T> class Prod {
private:
    T _prod;
public:
    using value_type = T;
    constexpr Prod() noexcept : _prod{null_value()} {}
    constexpr Prod(T value) : _prod{value} {}
    constexpr void sample(T value) { _prod = combine(_prod, value); }
    constexpr void merge(const Prod &rhs) { _prod = combine(_prod, rhs._prod); }
    constexpr T result() const { return _prod; }
    static constexpr Aggr enum_value() { return Aggr::PROD; }
    static constexpr T null_value() { return 1; }
    static constexpr T combine(T a, T b) { return (a * b); }
};

template <typename T> class Sum {
private:
    T _sum;
public:
    using value_type = T;
    constexpr Sum() noexcept : _sum{null_value()} {}
    constexpr Sum(T value) : _sum{value} {}
    constexpr void sample(T value) { _sum = combine(_sum, value); }
    constexpr void merge(const Sum &rhs) { _sum = combine(_sum, rhs._sum); }
    constexpr T result() const { return _sum; }
    static constexpr Aggr enum_value() { return Aggr::SUM; }
    static constexpr T null_value() { return 0; }
    static constexpr T combine(T a, T b) { return (a + b); }
};

template <typename T> class Max {
private:
    T _max;
public:
    using value_type = T;
    constexpr Max() noexcept : _max{null_value()} {}
    constexpr Max(T value) : _max{value} {}
    constexpr void sample(T value) { _max = combine(_max, value); }
    constexpr void merge(const Max &rhs) { _max = combine(_max, rhs._max); }
    constexpr T result() const { return _max; }
    static constexpr Aggr enum_value() { return Aggr::MAX; }
    static constexpr T null_value() { return -std::numeric_limits<T>::infinity(); }
    static constexpr T combine(T a, T b) { return std::max(a,b); }
};

template <typename T> class Median {
private:
    std::vector<T> _seen;
public:
    using value_type = T;
    constexpr Median() noexcept : _seen() {}
    constexpr Median(T value) : _seen({value}) {}
    constexpr void sample(T value) { _seen.push_back(value); }
    constexpr void merge(const Median &rhs) {
        for (T value: rhs._seen) {
            _seen.push_back(value);
        }
    };
    T result() const {
        if (_seen.empty()) {
            return std::numeric_limits<T>::quiet_NaN();
        }
        std::vector<T> tmp;
        tmp.reserve(_seen.size());
        for (T value: _seen) {
            if (!std::isnan(value)) {
                tmp.push_back(value);
            } else {
                return std::numeric_limits<T>::quiet_NaN();
            }
        }
        size_t n = (tmp.size() / 2);
        std::nth_element(tmp.begin(), tmp.begin() + n, tmp.end());
        T result = tmp[n]; // the nth element
        if ((tmp.size() % 2) == 0) {
            result += *std::max_element(tmp.begin(), tmp.begin() + n);
            result /= T{2};
        }
        return result;
    }
    static constexpr Aggr enum_value() { return Aggr::MEDIAN; }
};

template <typename T> class Min {
private:
    T _min;
public:
    using value_type = T;
    constexpr Min() noexcept : _min{null_value()} {}
    constexpr Min(T value) : _min{value} {}
    constexpr void sample(T value) { _min = combine(_min, value); }
    constexpr void merge(const Min &rhs) { _min = combine(_min, rhs._min); }
    constexpr T result() const { return _min; }
    static constexpr Aggr enum_value() { return Aggr::MIN; }
    static constexpr T null_value() { return std::numeric_limits<T>::infinity(); }
    static constexpr T combine(T a, T b) { return std::min(a,b); }
};

} // namespace vespalib::eval::aggr

struct TypifyAggr {
    template <template<typename> typename TT> using Result = TypifyResultSimpleTemplate<TT>;
    template <typename F> static decltype(auto) resolve(Aggr aggr, F &&f) {
        switch (aggr) {
        case Aggr::AVG:    return f(Result<aggr::Avg>());
        case Aggr::COUNT:  return f(Result<aggr::Count>());
        case Aggr::PROD:   return f(Result<aggr::Prod>());
        case Aggr::SUM:    return f(Result<aggr::Sum>());
        case Aggr::MAX:    return f(Result<aggr::Max>());
        case Aggr::MEDIAN: return f(Result<aggr::Median>());
        case Aggr::MIN:    return f(Result<aggr::Min>());
        }
        abort();
    }
};

} // namespace vespalib::eval
