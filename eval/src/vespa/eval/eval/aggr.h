// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/stllike/string.h>
#include <limits>
#include <vector>
#include <map>

namespace vespalib {

class Stash;

namespace eval {

struct BinaryOperation;

/**
 * Enumeration of all different aggregators that are allowed to be
 * used in tensor reduce expressions.
 **/
enum class Aggr { AVG, COUNT, PROD, SUM, MAX, MIN };

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
    virtual ~Aggregator();
    static Aggregator &create(Aggr aggr, Stash &stash);
    static std::vector<Aggr> list();
};

namespace aggr {

template <typename T> class Avg {
private:
    T _sum;
    size_t _cnt;
public:
    constexpr Avg() : _sum{0}, _cnt{0} {}
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
};

template <typename T> class Count {
private:
    size_t _cnt;
public:
    constexpr Count() : _cnt{0} {}
    constexpr Count(T) : _cnt{1} {}
    constexpr void sample(T) { ++_cnt; }
    constexpr void merge(const Count &rhs) { _cnt += rhs._cnt; }
    constexpr T result() const { return _cnt; }
};

template <typename T> class Prod {
private:
    T _prod;
public:
    constexpr Prod() : _prod{1} {}
    constexpr Prod(T value) : _prod{value} {}
    constexpr void sample(T value) { _prod *= value; }
    constexpr void merge(const Prod &rhs) { _prod *= rhs._prod; }
    constexpr T result() const { return _prod; }
};

template <typename T> class Sum {
private:
    T _sum;
public:
    constexpr Sum() : _sum{0} {}
    constexpr Sum(T value) : _sum{value} {}
    constexpr void sample(T value) { _sum += value; }
    constexpr void merge(const Sum &rhs) { _sum += rhs._sum; }
    constexpr T result() const { return _sum; }
};

template <typename T> class Max {
private:
    T _max;
public:
    constexpr Max() : _max{-std::numeric_limits<T>::infinity()} {}
    constexpr Max(T value) : _max{value} {}
    constexpr void sample(T value) { _max = std::max(_max, value); }
    constexpr void merge(const Max &rhs) { _max = std::max(_max, rhs._max); }
    constexpr T result() const { return _max; }
};

template <typename T> class Min {
private:
    T _min;
public:
    constexpr Min() : _min{std::numeric_limits<T>::infinity()} {}
    constexpr Min(T value) : _min{value} {}
    constexpr void sample(T value) { _min = std::min(_min, value); }
    constexpr void merge(const Min &rhs) { _min = std::min(_min, rhs._min); }
    constexpr T result() const { return _min; }
};

} // namespave vespalib::eval::aggr

struct TypifyAggr {
    template <template<typename> typename TT> using Result = TypifyResultSimpleTemplate<TT>;
    template <typename F> static decltype(auto) resolve(Aggr aggr, F &&f) {
        switch (aggr) {
        case Aggr::AVG:   return f(Result<aggr::Avg>());
        case Aggr::COUNT: return f(Result<aggr::Count>());
        case Aggr::PROD:  return f(Result<aggr::Prod>());
        case Aggr::SUM:   return f(Result<aggr::Sum>());
        case Aggr::MAX:   return f(Result<aggr::Max>());
        case Aggr::MIN:   return f(Result<aggr::Min>());
        }
        abort();
    }
};

} // namespace vespalib::eval
} // namespace vespalib
