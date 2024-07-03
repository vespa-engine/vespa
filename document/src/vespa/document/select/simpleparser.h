// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "node.h"
#include "valuenodes.h"
#include "operator.h"

namespace document::select::simple {

class Parser {
public:
    virtual ~Parser() = default;
    virtual bool parse(std::string_view s) = 0;
    std::string_view getRemaining() const { return _remaining; }
protected:
    void setRemaining(std::string_view s) { _remaining = s; }
    void setRemaining(std::string_view s, size_t fromPos);
private:
    std::string_view _remaining;
};

class NodeResult {
public:
    //TODO Dirty, should force use of std::move
    Node::UP getNode() { return std::move(_node); }
protected:
    void setNode(Node::UP node) { _node = std::move(node); }
private:
    Node::UP            _node;
};

class ValueResult {
public:
    //TODO Dirty, should force use of std::move
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
    explicit IdSpecParser(const BucketIdFactory& bucketIdFactory) noexcept
        : _bucketIdFactory(bucketIdFactory)
    {}
    bool parse(std::string_view s) override;
    const IdValueNode & getId() const { return static_cast<const IdValueNode &>(getValue()); }
    bool isUserSpec() const { return getId().getType() == IdValueNode::USER; }
private:
    const BucketIdFactory & _bucketIdFactory;
};

class OperatorParser : public Parser
{
public:
    bool parse(std::string_view s) override;
    const Operator * getOperator() const { return _operator; }
private:
    const Operator *_operator;
};

class StringParser : public Parser, public ValueResult
{
public:
    bool parse(std::string_view s) override;
};

class IntegerParser : public Parser, public ValueResult
{
public:
    bool parse(std::string_view s) override;
};

class SelectionParser : public Parser, public NodeResult
{
public:
    explicit SelectionParser(const BucketIdFactory& bucketIdFactory) noexcept
        : _bucketIdFactory(bucketIdFactory)
    {}
    bool parse(std::string_view s) override;
private:
    const BucketIdFactory & _bucketIdFactory;
};

}
