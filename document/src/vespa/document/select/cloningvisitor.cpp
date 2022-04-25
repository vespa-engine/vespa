// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cloningvisitor.h"
#include "valuenodes.h"
#include "branch.h"
#include "compare.h" 
#include "constant.h"
#include "invalidconstant.h"
#include "doctype.h" 

namespace document::select {

CloningVisitor::CloningVisitor()
    : _node(),
      _valueNode(),
      _constVal(false),
      _priority(-1),
      _fieldNodes(0u),
      _resultSet()
{ }


CloningVisitor::~CloningVisitor() = default;


void
CloningVisitor::visitAndBranch(const And &expr)
{
    int priority = AndPriority;
    expr.getLeft().visit(*this);
    bool lhsConstVal = _constVal;
    ResultSet lhsSet(_resultSet);
    setNodeParentheses(priority);
    std::unique_ptr<Node> lhs(std::move(_node));
    revisit();
    expr.getRight().visit(*this);
    _constVal &= lhsConstVal;
    _resultSet.calcAnd(lhsSet);
    setNodeParentheses(priority);
    std::unique_ptr<Node> rhs(std::move(_node));
    _priority = priority;
    _node = std::make_unique<And>(std::move(lhs), std::move(rhs), "and");
};


void
CloningVisitor::visitOrBranch(const Or &expr)
{
    int priority = OrPriority;
    expr.getLeft().visit(*this);
    bool lhsConstVal = _constVal;
    ResultSet lhsSet(_resultSet);
    setNodeParentheses(priority);
    std::unique_ptr<Node> lhs(std::move(_node));
    revisit();
    expr.getRight().visit(*this);
    _constVal &= lhsConstVal;
    _resultSet.calcOr(lhsSet);
    setNodeParentheses(priority);
    std::unique_ptr<Node> rhs(std::move(_node));
    _priority = priority;
    _node = std::make_unique<Or>(std::move(lhs), std::move(rhs), "or");
};


void
CloningVisitor::visitNotBranch(const Not &expr)
{
    int priority = ComparePriority;
    expr.getChild().visit(*this);
    setNodeParentheses(priority);
    _resultSet.calcNot();
    std::unique_ptr<Node> child(std::move(_node));
    _priority = priority;
    _node = std::make_unique<Not>(std::move(child), "not");
};


void
CloningVisitor::visitComparison(const Compare &expr)
{
    int priority = ComparePriority;
    expr.getLeft().visit(*this);
    bool lhsConstVal = _constVal;
    setValueNodeParentheses(priority);
    std::unique_ptr<ValueNode> lhs(std::move(_valueNode));
    revisit();
    expr.getRight().visit(*this);
    _constVal &= lhsConstVal;
    setValueNodeParentheses(priority);
    std::unique_ptr<ValueNode> rhs(std::move(_valueNode));
    const Operator &op(expr.getOperator());
    _priority = priority;
    _resultSet.fill(); // should be less if const
    _node = std::make_unique<Compare>(std::move(lhs), op, std::move(rhs), expr.getBucketIdFactory());
};


void
CloningVisitor::visitArithmeticValueNode(const ArithmeticValueNode &expr)
{
    expr.getLeft().visit(*this);
    bool lhsConstVal = _constVal;
    int lhsPriority = _priority;
    std::unique_ptr<ValueNode> lhs(std::move(_valueNode));
    revisit();
    expr.getRight().visit(*this);
    bool rhsConstVal = _constVal;
    int rhsPriority = _priority;
    std::unique_ptr<ValueNode> rhs(std::move(_valueNode));
    setArithmeticValueNode(expr,
                           std::move(lhs), lhsPriority, lhsConstVal,
                           std::move(rhs), rhsPriority, rhsConstVal);
}


void
CloningVisitor::visitFunctionValueNode(const FunctionValueNode &expr)
{
    int priority = FuncPriority;
    expr.getChild().visit(*this);
    setValueNodeParentheses(priority);
    std::unique_ptr<ValueNode> child(std::move(_valueNode));
    _priority = priority;
    _valueNode = std::make_unique<FunctionValueNode>(expr.getFunctionName(), std::move(child));
};


void
CloningVisitor::visitConstant(const Constant &expr)
{
    _constVal = true;
    _priority = ConstPriority;
    bool val = expr.getConstantValue();
    _resultSet.add(val ? Result::True : Result::False);
    _node = std::make_unique<Constant>(val);
}


void
CloningVisitor::visitInvalidConstant(const InvalidConstant &expr)
{
    (void) expr;
    _constVal = true;
    _priority = InvalidConstPriority;
    _resultSet.add(Result::Invalid);
    _node = std::make_unique<InvalidConstant>("invalid");
}


void
CloningVisitor::visitDocumentType(const DocType &expr)
{
    _constVal = false;
    _priority = DocumentTypePriority;
    _resultSet.add(Result::True);
    _resultSet.add(Result::False);
    _node = expr.clone();
}


void
CloningVisitor::visitIdValueNode(const IdValueNode &expr)
{
    _constVal = false;
    ++_fieldNodes; // needs document id, thus needs document
    _valueNode = expr.clone();
    _priority = IdPriority;
}


void
CloningVisitor::visitFieldValueNode(const FieldValueNode &expr)
{
    _constVal = false;
    ++_fieldNodes; // needs document id, thus needs document
    _valueNode = expr.clone();
    _priority = FieldValuePriority;
}


void
CloningVisitor::visitFloatValueNode(const FloatValueNode &expr)
{
    _constVal = true;
    _valueNode = expr.clone();
    _priority = FloatPriority;
}


void
CloningVisitor::visitVariableValueNode(const VariableValueNode &expr)
{
    _valueNode = std::make_unique<VariableValueNode>(expr.getVariableName());
    _priority = VariablePriority;
}


void
CloningVisitor::visitIntegerValueNode(const IntegerValueNode &expr)
{
    _constVal = true;
    _valueNode = expr.clone();
    _priority = IntegerPriority;
}


void
CloningVisitor::visitBoolValueNode(const BoolValueNode &expr)
{
    _constVal = true;
    _valueNode = expr.clone();
    _priority = BoolPriority;
}


void
CloningVisitor::visitCurrentTimeValueNode(const CurrentTimeValueNode &expr)
{
    _constVal = false;
    _valueNode = expr.clone();
    _priority = CurrentTimePriority;
}


void
CloningVisitor::visitStringValueNode(const StringValueNode &expr)
{
    _constVal = true;
    _valueNode = expr.clone();
    _priority = StringPriority;
}


void
CloningVisitor::visitNullValueNode(const NullValueNode &expr)
{
    _constVal = true;
    _valueNode = expr.clone();
    _priority = NullValPriority;
}

void
CloningVisitor::visitInvalidValueNode(const InvalidValueNode &expr)
{
    _constVal = true;
    _valueNode = expr.clone();
    _priority = InvalidValPriority;
}


void
CloningVisitor::setNodeParentheses(int priority)
{
    if (_priority < priority)
        _node->setParentheses();
}


void
CloningVisitor::setValueNodeParentheses(int priority)
{
    if (_priority < priority)
        _valueNode->setParentheses();
}


void
CloningVisitor::setArithmeticValueNode(const ArithmeticValueNode &expr,
                                       std::unique_ptr<ValueNode> lhs,
                                       int lhsPriority,
                                       bool lhsConstVal,
                                       std::unique_ptr<ValueNode> rhs,
                                       int rhsPriority,
                                       bool rhsConstVal)
{
    bool lassoc = false;
    bool rassoc = false;
    int priority = 0;
    switch(expr.getOperator()) {
    case ArithmeticValueNode::ADD:
        priority = AddPriority;
        lassoc = true;
        rassoc = true;
        break;
    case ArithmeticValueNode::SUB:
        priority = SubPriority;
        lassoc = true;
        break;
    case ArithmeticValueNode::MUL:
        priority = MulPriority;
        lassoc = true;
        rassoc = true;
        break;
    case ArithmeticValueNode::DIV:
        priority = DivPriority;
        lassoc = true;
        break;
    case ArithmeticValueNode::MOD:
        priority = ModPriority;
        lassoc = true;
        break;
    }
    if (lhsPriority < priority ||
        (lhsPriority == priority && !lassoc)) {
        lhs->setParentheses();
    }
    _constVal = lhsConstVal && rhsConstVal;
    if (rhsPriority < priority ||
        (rhsPriority == priority && !rassoc)) {
        rhs->setParentheses();
    }
    _priority = priority;
    _valueNode.reset(new ArithmeticValueNode(std::move(lhs),
                                             expr.getOperatorName(),
                                             std::move(rhs)));
}


void
CloningVisitor::swap(CloningVisitor &rhs)
{
    std::swap(_constVal, rhs._constVal);
    std::swap(_priority, rhs._priority);
    std::swap(_node, rhs._node);
    std::swap(_valueNode, rhs._valueNode);
    std::swap(_resultSet, rhs._resultSet);
    std::swap(_fieldNodes, rhs._fieldNodes);
}


void
CloningVisitor::revisit()
{
    _constVal = false;
    _priority = -1;
    _resultSet.clear();
}

}
