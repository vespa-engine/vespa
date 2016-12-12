// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "tensor_nodes.h"
#include "node_visitor.h"

namespace vespalib {
namespace eval {
namespace nodes {

void TensorSum   ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorMap   ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorJoin  ::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorReduce::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorRename::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorLambda::accept(NodeVisitor &visitor) const { visitor.visit(*this); }
void TensorConcat::accept(NodeVisitor &visitor) const { visitor.visit(*this); }

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

} // namespace vespalib::eval::nodes
} // namespace vespalib::eval
} // namespace vespalib
