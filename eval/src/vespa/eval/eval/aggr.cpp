// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "aggr.h"
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".eval.eval.aggr");

namespace vespalib {
namespace eval {

namespace {

struct Avg : Aggregator {
    double sum = 0.0;
    size_t cnt = 1;
    void first(double value) override {
        sum = value;
        cnt = 1;
    }
    void next(double value) override {
        sum += value;
        ++cnt;
    }
    double result() const override { return (sum / cnt); }
};

struct Count : Aggregator {
    size_t cnt = 0;
    void first(double) override { cnt = 1; }
    void next(double) override { ++cnt; }
    double result() const override { return cnt; }
};

struct Prod : Aggregator {
    double prod = 0.0;
    void first(double value) override { prod = value; }
    void next(double value) override { prod *= value; }
    double result() const override { return prod; }
};

struct Sum : Aggregator {
    double sum = 0.0;
    void first(double value) override { sum = value; }
    void next(double value) override { sum += value; }
    double result() const override { return sum; }
};

struct Max : Aggregator {
    double max = 0.0;
    void first(double value) override { max = value; }
    void next(double value) override { max = std::max(max, value); }
    double result() const override { return max; }
};

struct Min : Aggregator {
    double min = 0.0;
    void first(double value) override { min = value; }
    void next(double value) override { min = std::min(min, value); }
    double result() const override { return min; }
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
    add(Aggr::AVG,   "avg");
    add(Aggr::COUNT, "count");
    add(Aggr::PROD,  "prod");
    add(Aggr::SUM,   "sum");
    add(Aggr::MAX,   "max");
    add(Aggr::MIN,   "min");
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
    switch (aggr) {
    case Aggr::AVG:   return stash.create<Avg>();
    case Aggr::COUNT: return stash.create<Count>();
    case Aggr::PROD:  return stash.create<Prod>();
    case Aggr::SUM:   return stash.create<Sum>();
    case Aggr::MAX:   return stash.create<Max>();
    case Aggr::MIN:   return stash.create<Min>();
    }
    LOG_ABORT("should not be reached");
}

} // namespace vespalib::eval
} // namespace vespalib
