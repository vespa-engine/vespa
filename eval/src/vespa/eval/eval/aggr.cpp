// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "aggr.h"
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.eval.aggr");

namespace vespalib {
namespace eval {

namespace {

template <typename T>
struct Wrapper : Aggregator {
    T aggr;
    virtual void first(double value) final override { aggr = T{value}; }
    virtual void next(double value) final override { aggr.sample(value); }
    virtual double result() const final override { return aggr.result(); }
    virtual Aggr enum_value() const final override { return T::enum_value(); }
};

} // namespace vespalib::eval::<unnamed>

const AggrNames AggrNames::_instance;

void
AggrNames::add(Aggr aggr, const vespalib::string &name)
{
    _name_aggr_map[name] = aggr;
    _aggr_name_map[aggr] = name;
}

AggrNames::AggrNames()
    : _name_aggr_map(),
      _aggr_name_map()
{
    add(Aggr::AVG,    "avg");
    add(Aggr::COUNT,  "count");
    add(Aggr::PROD,   "prod");
    add(Aggr::SUM,    "sum");
    add(Aggr::MAX,    "max");
    add(Aggr::MEDIAN, "median");
    add(Aggr::MIN,    "min");
}

const vespalib::string *
AggrNames::name_of(Aggr aggr)
{
    const auto &map = _instance._aggr_name_map;
    auto result = map.find(aggr);
    if (result == map.end()) {
        return nullptr;
    }
    return &(result->second);
}

const Aggr *
AggrNames::from_name(const vespalib::string &name)
{
    const auto &map = _instance._name_aggr_map;
    auto result = map.find(name);
    if (result == map.end()) {
        return nullptr;
    }
    return &(result->second);
}

Aggregator::~Aggregator()
{
}

Aggregator &
Aggregator::create(Aggr aggr, Stash &stash)
{
    return TypifyAggr::resolve(aggr, [&stash](auto t)->Aggregator&
                               {
                                   using T = typename decltype(t)::template templ<double>;
                                   return stash.create<Wrapper<T>>();
                               });
}

std::vector<Aggr>
Aggregator::list()
{
    return std::vector<Aggr>({ Aggr::AVG, Aggr::COUNT, Aggr::PROD,
                               Aggr::SUM, Aggr::MAX,   Aggr::MEDIAN,
                               Aggr::MIN });
}

} // namespace vespalib::eval
} // namespace vespalib
