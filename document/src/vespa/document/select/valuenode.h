// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::ValueNode
 * @ingroup select
 *
 * @brief Node representing a value in the tree
 *
 * @author H�kon Humberset
 * @date 2007-04-20
 * @version $Id$
 */

#pragma once

#include "value.h"
#include "context.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/bucket/bucketdistribution.h>
#include <vespa/document/bucket/bucketidfactory.h>

namespace document {

class Document;
class Field;

namespace select {

class ValueNode : public Printable
{
public:
    using UP = std::unique_ptr<ValueNode>;

    ValueNode() : _parentheses(false) {}
    virtual ~ValueNode() {}

    void setParentheses() { _parentheses = true; }
    void clearParentheses() { _parentheses = false; }
    bool hadParentheses() const { return _parentheses; }

    virtual std::unique_ptr<Value>
    getValue(const Context& context) const  = 0;

    virtual std::unique_ptr<Value>
    traceValue(const Context &context, std::ostream &out) const {
        return defaultTrace(getValue(context), out);
    }

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const override = 0;
    virtual void visit(Visitor&) const = 0;
    virtual ValueNode::UP clone() const = 0;
private:
    bool _parentheses; // Set to true if parentheses was used around this part
                       // Set such that we can recreate original query in print.
protected:
    ValueNode::UP wrapParens(ValueNode* node) const {
        ValueNode::UP ret(node);
        if (_parentheses) {
            ret->setParentheses();
        }
        return ret;
    }

    std::unique_ptr<Value> defaultTrace(std::unique_ptr<Value> val, std::ostream& out) const;
};

class InvalidValueNode : public ValueNode
{
    vespalib::string _name;
public:
    InvalidValueNode(const vespalib::stringref & name);

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::unique_ptr<Value>(new InvalidValue());
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new InvalidValueNode(_name));
    }
};

class NullValueNode : public ValueNode
{
    vespalib::string _name;
public:
    NullValueNode(const vespalib::stringref & name);

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::unique_ptr<Value>(new NullValue());
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new NullValueNode(_name));
    }
};

class StringValueNode : public ValueNode
{
    vespalib::string _value;
public:
    StringValueNode(const vespalib::stringref & val);

    const vespalib::string& getValue() const { return _value; }

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::unique_ptr<Value>(new StringValue(_value));
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new StringValueNode(_value));
    }
};

class IntegerValueNode : public ValueNode
{
    int64_t _value;
    bool _isBucketValue;
public:
    IntegerValueNode(int64_t val, bool isBucketValue)
        : _value(val), _isBucketValue(isBucketValue) {}

    int64_t getValue() const { return _value; }

    virtual std::unique_ptr<Value> getValue(const Context&) const override {
        return std::unique_ptr<Value>(new IntegerValue(_value, _isBucketValue));
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new IntegerValueNode(_value, _isBucketValue));
    }
};

class CurrentTimeValueNode : public ValueNode
{
public:
    int64_t getValue() const;

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::unique_ptr<Value>(new IntegerValue(getValue(), false));
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new CurrentTimeValueNode);
    }
};

class VariableValueNode : public ValueNode
{
    vespalib::string _value;
public:
    VariableValueNode(const vespalib::string & variableName) : _value(variableName) {}

    const vespalib::string& getVariableName() const { return _value; }

    std::unique_ptr<Value> getValue(const Context& context) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new VariableValueNode(_value));
    }
};

class FloatValueNode : public ValueNode
{
    double _value;
public:
    FloatValueNode(double val) : _value(val) {}

    double getValue() const { return _value; }

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::unique_ptr<Value>(new FloatValue(_value));
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new FloatValueNode(_value));
    }
};

class FieldValueNode : public ValueNode
{
    vespalib::string _doctype;
    vespalib::string _fieldExpression;
    vespalib::string _fieldName;
    mutable FieldPath _fieldPath;

public:
    FieldValueNode(const vespalib::string& doctype, const vespalib::string& fieldExpression);
    FieldValueNode(const FieldValueNode &);
    FieldValueNode & operator = (const FieldValueNode &);
    FieldValueNode(FieldValueNode &&) = default;
    FieldValueNode & operator = (FieldValueNode &&) = default;
    ~FieldValueNode();

    const vespalib::string& getDocType() const { return _doctype; }
    const vespalib::string& getRealFieldName() const { return _fieldName; }
    const vespalib::string& getFieldName() const { return _fieldExpression; }

    std::unique_ptr<Value> getValue(const Context& context) const override;
    std::unique_ptr<Value> traceValue(const Context &context, std::ostream& out) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new FieldValueNode(_doctype, _fieldExpression));
    }

    static vespalib::string extractFieldName(const std::string & fieldExpression);

private:

    void initFieldPath(const DocumentType&) const;
};

class IdValueNode : public ValueNode
{
public:
    enum Type { SCHEME, NS, TYPE, USER, GROUP, GID, SPEC, BUCKET, ORDER, ALL };

    IdValueNode(const BucketIdFactory& bucketIdFactory,
                const vespalib::stringref & name, const vespalib::stringref & type,
                int widthBits = -1, int divisionBits = -1);

    Type getType() const { return _type; }

    std::unique_ptr<Value> getValue(const Context& context) const override;

    std::unique_ptr<Value> getValue(const DocumentId& id) const;

    std::unique_ptr<Value> traceValue(const Context& context, std::ostream &out) const override;

    std::unique_ptr<Value> traceValue(const DocumentId& val, std::ostream& out) const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new IdValueNode(_bucketIdFactory, _id, _typestring, _widthBits, _divisionBits));
    }

    int getWidthBits() const { return _widthBits; }
    int getDivisionBits() const { return _divisionBits; }

private:
    const BucketIdFactory& _bucketIdFactory;
    vespalib::string _id;
    vespalib::string _typestring;
    Type _type;
    int _widthBits;
    int _divisionBits;
};

class SearchColumnValueNode : public ValueNode
{
public:
    SearchColumnValueNode(const BucketIdFactory& bucketIdFactory,
                          const vespalib::stringref & name,
                          int numColumns);

    int getColumns() { return _numColumns; }

    std::unique_ptr<Value> getValue(const Context& context) const override;
    std::unique_ptr<Value> getValue(const DocumentId& id) const;
    std::unique_ptr<Value> traceValue(const Context& context, std::ostream &out) const override;
    std::unique_ptr<Value> traceValue(const DocumentId& val, std::ostream& out) const;
    
    int64_t getValue(const BucketId& bucketId) const;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new SearchColumnValueNode(_bucketIdFactory, _id, _numColumns));
}

private:
    const BucketIdFactory& _bucketIdFactory;
    vespalib::string _id;
    int _numColumns;
    BucketDistribution _distribution;
};

class FunctionValueNode : public ValueNode
{
public:
    enum Function { LOWERCASE, HASH, ABS };

    FunctionValueNode(const vespalib::stringref & name, std::unique_ptr<ValueNode> src);

    Function getFunction() const { return _function; }
    const vespalib::string &getFunctionName(void) const { return _funcname; }

    std::unique_ptr<Value> getValue(const Context& context) const override {
        return getValue(_source->getValue(context));
    }

    std::unique_ptr<Value> traceValue(const Context &context, std::ostream& out) const override {
        return traceValue(_source->getValue(context), out);
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new FunctionValueNode(_funcname, _source->clone()));
    }

    const ValueNode& getChild() const { return *_source; }

private:
    Function _function;
    vespalib::string _funcname;
    std::unique_ptr<ValueNode> _source;

    virtual std::unique_ptr<Value> getValue(std::unique_ptr<Value> val) const;
    virtual std::unique_ptr<Value> traceValue(std::unique_ptr<Value> val,
                                            std::ostream& out) const;
};

class ArithmeticValueNode : public ValueNode
{
public:
    enum Operator { ADD, SUB, MUL, DIV, MOD };

    ArithmeticValueNode(std::unique_ptr<ValueNode> left,
                        const vespalib::stringref & op,
                        std::unique_ptr<ValueNode> right);

    Operator getOperator() const { return _operator; }
    const char* getOperatorName() const;

    std::unique_ptr<Value>
    getValue(const Context& context) const override {
        return getValue(_left->getValue(context), _right->getValue(context));
    }
    
    std::unique_ptr<Value>
    traceValue(const Context &context, std::ostream& out) const override {
        return traceValue(_left->getValue(context), _right->getValue(context), out);
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new ArithmeticValueNode(_left->clone(),
                                                  getOperatorName(),
                                                  _right->clone()));
    }

    const ValueNode& getLeft() const { return *_left; }
    const ValueNode& getRight() const { return *_right; }

private:
    Operator _operator;
    std::unique_ptr<ValueNode> _left;
    std::unique_ptr<ValueNode> _right;

    virtual std::unique_ptr<Value> getValue(std::unique_ptr<Value> lval,
                                          std::unique_ptr<Value> rval) const;
    virtual std::unique_ptr<Value> traceValue(std::unique_ptr<Value> lval,
                                            std::unique_ptr<Value> rval,
                                            std::ostream&) const;
};

} // select
} // document
