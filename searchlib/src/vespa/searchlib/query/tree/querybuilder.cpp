// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".querybuilder");

#include "querybuilder.h"

#include "intermediate.h"

using vespalib::string;
using namespace search::query;

void QueryBuilderBase::reportError(const vespalib::string &msg) {
    if (!hasError()) {
        _error_msg = msg;
    }
}

QueryBuilderBase::QueryBuilderBase()
    : _root(),
      _nodes(),
      _error_msg() {
}

QueryBuilderBase::~QueryBuilderBase() {
    reset();
}

void QueryBuilderBase::addCompleteNode(Node *n)
{
    Node::UP node(n);

    if (hasError()) {
        return;
    }
    if (_nodes.empty()) {
        if (!_root.get()) {
            _root = std::move(node);
            return;
        }
        reportError("QueryBuilder got invalid node structure.");
        return;
    }

    assert(_nodes.top().remaining_child_count > 0);
    _nodes.top().node->append(std::move(node));
    if (--_nodes.top().remaining_child_count == 0) {
        Node *completed(_nodes.top().node);
        _nodes.pop();
        addCompleteNode(completed);
    }
}

void QueryBuilderBase::addIntermediateNode(Intermediate *n, int child_count)
{
    Intermediate::UP node(n);
    if (!hasError()) {
        if (_root.get()) {
            reportError("QueryBuilder got invalid node structure.");
        } else {
            node->reserve(child_count);
            WeightOverride weight_override;
            if (!_nodes.empty()) {
                weight_override = _nodes.top().weight_override;
            }
            _nodes.push(NodeInfo(node.release(), child_count));
            _nodes.top().weight_override = weight_override;
            if (child_count == 0) {
                Node *completed(_nodes.top().node);
                _nodes.pop();
                addCompleteNode(completed);
            }
        }
    }
}

void QueryBuilderBase::setWeightOverride(const Weight &weight) {
    assert(!_nodes.empty());
    _nodes.top().weight_override = WeightOverride(weight);
}

Node::UP QueryBuilderBase::build() {
    if (!_root.get()) {
        reportError("Trying to build incomplete query tree.");
    }
    if (!_nodes.empty()) {
        reportError("QueryBuilder got invalid node structure.");
    }
    if (hasError()) {
        return Node::UP();
    }
    return std::move(_root);
}

void QueryBuilderBase::reset() {
    while (!_nodes.empty()) {
        delete _nodes.top().node;
        _nodes.pop();
    }
    _root.reset(0);
    _error_msg = "";
}
