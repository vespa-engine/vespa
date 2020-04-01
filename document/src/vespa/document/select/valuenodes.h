// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "valuenode.h"
#include <vespa/document/base/fieldpath.h>

namespace document {
    class BucketIdFactory;
    class DocumentId;
    class BucketId;
    class DocumentType;
}

namespace document::select {

class InvalidValueNode : public ValueNode
{
    vespalib::string _name;
public:
    InvalidValueNode(vespalib::stringref name);

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::make_unique<InvalidValue>();
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new InvalidValueNode(_name));
    }
};

class NullValueNode : public ValueNode
{
public:
    NullValueNode();

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::make_unique<NullValue>();
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void visit(Visitor& visitor) const override;

    ValueNode::UP clone() const override {
        return wrapParens(new NullValueNode());
    }
};

class StringValueNode : public ValueNode
{
    vespalib::string _value;
public:
    explicit StringValueNode(vespalib::stringref val);

    const vespalib::string& getValue() const { return _value; }

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::make_unique<StringValue>(_value);
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

    std::unique_ptr<Value> getValue(const Context&) const override {
        return std::make_unique<IntegerValue>(_value, _isBucketValue);
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
        return std::make_unique<IntegerValue>(getValue(), false);
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
    // TODO stringref
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
        return std::make_unique<FloatValue>(_value);
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
    FieldValueNode(const FieldValueNode &) = delete;
    FieldValueNode & operator = (const FieldValueNode &) = delete;
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

class FunctionValueNode;

// Only used by the parser to build a partial field expression. Never part of
// an AST tree returned to the caller.
class FieldExprNode final : public ValueNode {
    std::unique_ptr<FieldExprNode> _left_expr;
    vespalib::string _right_expr;
public:
    explicit FieldExprNode(const vespalib::string& doctype) : _left_expr(), _right_expr(doctype) {}
    FieldExprNode(std::unique_ptr<FieldExprNode> left_expr, vespalib::stringref right_expr)
        : _left_expr(std::move(left_expr)), _right_expr(right_expr)
    {}
    FieldExprNode(const FieldExprNode &) = delete;
    FieldExprNode & operator = (const FieldExprNode &) = delete;
    FieldExprNode(FieldExprNode &&) = default;
    FieldExprNode & operator = (FieldExprNode &&) = default;
    ~FieldExprNode();

    std::unique_ptr<FieldValueNode> convert_to_field_value() const;
    std::unique_ptr<FunctionValueNode> convert_to_function_call() const;
private:
    void build_mangled_expression(vespalib::string& dest) const;
    const vespalib::string& resolve_doctype() const;

    // These are not used, can just return dummy values.
    std::unique_ptr<Value> getValue(const Context& context) const override {
        (void) context;
        return std::unique_ptr<Value>();
    }
    std::unique_ptr<Value> traceValue(const Context &context, std::ostream& out) const override {
        (void) context;
        (void) out;
        return std::unique_ptr<Value>();
    }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override {
        (void) out;
        (void) verbose;
        (void) indent;
    }
    void visit(Visitor& visitor) const override {
        (void) visitor;
    }

    ValueNode::UP clone() const override {
        if (_left_expr) {
            return wrapParens(new FieldExprNode(std::unique_ptr<FieldExprNode>(
                    static_cast<FieldExprNode*>(_left_expr->clone().release())), _right_expr));
        } else {
            return wrapParens(new FieldExprNode(_right_expr));
        }
    }
};

class IdValueNode : public ValueNode
{
public:
    enum Type { SCHEME, NS, TYPE, USER, GROUP, GID, SPEC, BUCKET, ALL };

    IdValueNode(const BucketIdFactory& bucketIdFactory,
                vespalib::stringref name, vespalib::stringref type,
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

class FunctionValueNode : public ValueNode
{
public:
    enum Function { LOWERCASE, HASH, ABS };

    FunctionValueNode(vespalib::stringref name, std::unique_ptr<ValueNode> src);

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
                        vespalib::stringref op,
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

}
