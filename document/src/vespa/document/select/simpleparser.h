// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "node.h"
#include "valuenodes.h"
#include "operator.h"

namespace document::select::simple {

class Parser {
public:
    virtual ~Parser() { }
    virtual bool parse(vespalib::stringref s) = 0;
    vespalib::stringref getRemaining() const { return _remaining; }
protected:
    void setRemaining(vespalib::stringref s) { _remaining = s; }
private:
    vespalib::stringref _remaining;
};

class NodeResult {
public:
    Node::UP getNode() { return std::move(_node); }
protected:
    void setNode(Node::UP node) { _node = std::move(node); }
private:
    Node::UP            _node;
};

class ValueResult {
public:
    ValueNode::UP stealValue() { return std::move(_value); }
    const ValueNode & getValue() const { return *_value; }
protected:
    void setValue(ValueNode::UP node) { _value = std::move(node); }
private:
    ValueNode::UP            _value;
};

class IdSpecParser : public Parser, public ValueResult
{
public:
    IdSpecParser(const BucketIdFactory& bucketIdFactory) :
        _bucketIdFactory(bucketIdFactory)
    {}
    bool parse(vespalib::stringref s) override;
    const IdValueNode & getId() const { return static_cast<const IdValueNode &>(getValue()); }
    bool isUserSpec() const { return getId().getType() == IdValueNode::USER; }
private:
    const BucketIdFactory & _bucketIdFactory;
};

class OperatorParser : public Parser
{
public:
    bool parse(vespalib::stringref s) override;
    const Operator * getOperator() const { return _operator; }
private:
    const Operator *_operator;
};

class StringParser : public Parser, public ValueResult
{
public:
    bool parse(vespalib::stringref s) override;
};

class IntegerParser : public Parser, public ValueResult
{
public:
    bool parse(vespalib::stringref s) override;
};

class SelectionParser : public Parser, public NodeResult
{
public:
    SelectionParser(const BucketIdFactory& bucketIdFactory) :
        _bucketIdFactory(bucketIdFactory)
    {}
    bool parse(vespalib::stringref s) override;
private:
    const BucketIdFactory & _bucketIdFactory;
};

}
