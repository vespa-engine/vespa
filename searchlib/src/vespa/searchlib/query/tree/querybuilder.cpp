// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querybuilder.h"
#include "intermediate.h"
#include <vespa/vespalib/util/classname.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

using vespalib::string;
using vespalib::make_string;
using vespalib::getClassName;

using namespace search::query;

void QueryBuilderBase::reportError(const string &msg) {
    if (!hasError()) {
        _error_msg = msg;
    }
}


void QueryBuilderBase::reportError(const string &msg, const Node & incomming, const Node & root) {
    reportError(make_string("%s: QueryBuilder got invalid node structure. Incomming node is '%s', while root is non-null('%s')",
                            msg.c_str(), getClassName(incomming).c_str(), getClassName(root).c_str()));
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
        if (!_root) {
            _root = std::move(node);
        } else {
            reportError("QueryBuilderBase::addCompleteNode", *node, *_root);
        }
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
        if (_root) {
            reportError("QueryBuilderBase::addIntermediateNode", *node, *_root);
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
    if ( !hasError() ) {
        _nodes.top().weight_override = WeightOverride(weight);
    }
}

Node::UP QueryBuilderBase::build() {
    if (!_nodes.empty()) {
        reportError("QueryBuilderBase::build: QueryBuilder got invalid node structure. _nodes are not empty.");
    } else if (!_root) {
        reportError("QueryBuilderBase::build: Trying to build incomplete query tree.");
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
