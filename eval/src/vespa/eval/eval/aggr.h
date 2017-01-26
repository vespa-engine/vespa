// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace vespalib {

class Stash;

namespace eval {

struct BinaryOperation;

enum class Aggr { AVG, COUNT, PROD, SUM, MAX, MIN };
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

struct Aggregator {
    virtual void first(double value) = 0;
    virtual void next(double value) = 0;
    virtual double result() const = 0;
    virtual ~Aggregator();
    static Aggregator &create(Aggr aggr, Stash &stash);
};

} // namespace vespalib::eval
} // namespace vespalib
