// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include "resultset.h"
#include <memory>

namespace document {

namespace select {

class Node;
class ValueNode;

class CloningVisitor : public Visitor
{
protected:
    std::unique_ptr<Node> _node;
    std::unique_ptr<ValueNode> _valueNode;
    bool _constVal;
    int _priority;
    uint32_t _fieldNodes;
    ResultSet _resultSet;

    static const int OrPriority = 100;
    static const int AndPriority = 200;
    static const int NotPriority = 300;
    static const int ComparePriority = 400;
    static const int AddPriority = 500;
    static const int SubPriority = 500;
    static const int MulPriority = 600;
    static const int DivPriority = 600;
    static const int ModPriority = 700;
    static const int DocumentTypePriority = 1000;
    static const int FieldValuePriority = 1000;
    static const int InvalidConstPriority = 1000;
    static const int InvalidValPriority = 1000;
    static const int ConstPriority = 1000;
    static const int FuncPriority = 1000;
    static const int VariablePriority = 1000;
    static const int FloatPriority = 1000;
    static const int IntegerPriority = 1000;
    static const int CurrentTimePriority = 1000;
    static const int StringPriority = 1000;
    static const int NullValPriority = 1000;
    static const int IdPriority = 1000;
    static const int SearchColPriority = 1000;

public:
    CloningVisitor(void);

    virtual
    ~CloningVisitor(void);

    virtual void
    visitAndBranch(const And &expr);

    virtual void
    visitOrBranch(const Or &expr);

    virtual void
    visitNotBranch(const Not &expr);

    virtual void
    visitComparison(const Compare &expr);

    virtual void
    visitArithmeticValueNode(const ArithmeticValueNode &expr);

    virtual void
    visitFunctionValueNode(const FunctionValueNode &expr);

    virtual void
    visitConstant(const Constant &expr);

    virtual void
    visitInvalidConstant(const InvalidConstant &expr);

    virtual void
    visitDocumentType(const DocType &expr);

    virtual void
    visitIdValueNode(const IdValueNode &expr);

    virtual void
    visitSearchColumnValueNode(const SearchColumnValueNode &expr);

    virtual void
    visitFieldValueNode(const FieldValueNode &expr);

    virtual void
    visitFloatValueNode(const FloatValueNode &expr);

    virtual void
    visitVariableValueNode(const VariableValueNode &expr);

    virtual void
    visitIntegerValueNode(const IntegerValueNode &expr);

    virtual void
    visitCurrentTimeValueNode(const CurrentTimeValueNode &expr);

    virtual void
    visitStringValueNode(const StringValueNode &expr);

    virtual void
    visitNullValueNode(const NullValueNode &expr);

    virtual void
    visitInvalidValueNode(const InvalidValueNode &expr);

    std::unique_ptr<Node> &
    getNode(void)
    {
        return _node;
    }
    
    std::unique_ptr<ValueNode> &
    getValueNode(void)
    {
        return _valueNode;
    }

    void
    setNodeParentheses(int priority);

    void
    setValueNodeParentheses(int priority);

    void
    setArithmeticValueNode(const ArithmeticValueNode &expr,
                           std::unique_ptr<ValueNode> lhs,
                           int lhsPriority,
                           bool lhsConstVal,
                           std::unique_ptr<ValueNode> rhs,
                           int rhsPriority,
                           bool rhsConstVal);

    void
    swap(CloningVisitor &rhs);

    void
    revisit(void);
};

}

}

