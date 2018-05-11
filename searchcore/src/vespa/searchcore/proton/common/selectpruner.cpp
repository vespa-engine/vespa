// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "select_utils.h"
#include "selectpruner.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/select/branch.h>
#include <vespa/document/select/compare.h>
#include <vespa/document/select/constant.h>
#include <vespa/document/select/doctype.h>
#include <vespa/document/select/invalidconstant.h>
#include <vespa/document/select/valuenodes.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/iattributemanager.h>

using document::select::And;
using document::select::Compare;
using document::select::Constant;
using document::select::DocType;
using document::select::Not;
using document::select::Or;
using document::select::ArithmeticValueNode;
using document::select::FunctionValueNode;
using document::select::IdValueNode;
using document::select::FieldValueNode;
using document::select::FloatValueNode;
using document::select::VariableValueNode;
using document::select::IntegerValueNode;
using document::select::InvalidConstant;
using document::select::CurrentTimeValueNode;
using document::select::StringValueNode;
using document::select::NullValueNode;
using document::select::InvalidValueNode;
using document::select::Node;
using document::select::Result;
using document::select::ResultSet;
using document::select::ResultList;
using document::select::FunctionOperator;
using document::select::Operator;
using document::select::InvalidValue;
using document::select::ValueNode;
using document::FieldPath;
using document::Field;
using document::FieldNotFoundException;
using search::AttributeGuard;
using search::attribute::CollectionType;

namespace proton {

SelectPrunerBase::SelectPrunerBase(const vespalib::string &docType,
                                   const search::IAttributeManager *amgr,
                                   const document::Document &emptyDoc,
                                   const document::DocumentTypeRepo &repo,
                                   bool hasFields,
                                   bool hasDocuments)
    : _docType(docType),
      _amgr(amgr),
      _emptyDoc(emptyDoc),
      _repo(repo),
      _hasFields(hasFields),
      _hasDocuments(hasDocuments)
{
}

SelectPrunerBase::SelectPrunerBase(const SelectPrunerBase &rhs)
    : _docType(rhs._docType),
      _amgr(rhs._amgr),
      _emptyDoc(rhs._emptyDoc),
      _repo(rhs._repo),
      _hasFields(rhs._hasFields),
      _hasDocuments(rhs._hasDocuments)
{
}

SelectPruner::SelectPruner(const vespalib::string &docType,
                           const search::IAttributeManager *amgr,
                           const document::Document &emptyDoc,
                           const document::DocumentTypeRepo &repo,
                           bool hasFields,
                           bool hasDocuments)
    : CloningVisitor(),
      SelectPrunerBase(docType, amgr, emptyDoc, repo, hasFields, hasDocuments),
      _inverted(false),
      _wantInverted(false),
      _attrFieldNodes(0u)
{
}

SelectPruner::SelectPruner(const SelectPruner *rhs)
    : CloningVisitor(),
      SelectPrunerBase(*rhs),
      _inverted(false),
      _wantInverted(false),
      _attrFieldNodes(0u)
{
}


SelectPruner::~SelectPruner()
{
}


void
SelectPruner::visitAndBranch(const And &expr)
{
    SelectPruner lhs(this);
    SelectPruner rhs(this);
    if (_wantInverted) {
        lhs._wantInverted = true;
        rhs._wantInverted = true;
    }
    expr.getLeft().visit(lhs);
    expr.getRight().visit(rhs);
    if (lhs.getFieldNodes() - lhs.getAttrFieldNodes() >
        rhs.getFieldNodes() - rhs.getAttrFieldNodes()) {
        lhs.swap(rhs);
    }
    ResultSet lhsSet(lhs._resultSet);
    ResultSet rhsSet(rhs._resultSet);
    if (lhs._inverted) {
        lhsSet = lhsSet.calcNot();
    }
    if (rhs._inverted) {
        rhsSet = rhsSet.calcNot();
    }
    _resultSet = lhsSet.calcAnd(rhsSet);
    _priority = AndPriority;
    if (lhs._inverted && rhs._inverted) {
        // De Morgan's laws
        _inverted = true;
        _priority = OrPriority;
        _resultSet = _resultSet.calcNot();
    }
    _constVal = lhs._constVal && rhs._constVal;
    lhs.resolveTernaryConst(_inverted);
    rhs.resolveTernaryConst(_inverted);
    if (lhs.isFalse() || rhs.isFalse()) {
        // false and foo ==> false
        // foo and false ==> false
        setTernaryConst(_inverted);
        return;
    }
    if (lhs.isTrue()) {
        if (rhs.isTrue()) {
            // true and true ==> true
            setTernaryConst(!_inverted);
            return;
        }
        // true and foo ==> foo
        _node = std::move(rhs._node);
        _priority = rhs._priority;
        _inverted = rhs._inverted;
        _resultSet = rhs._resultSet;
        addNodeCount(rhs);
        return;
    } else if (rhs.isTrue()) {
        // foo and true ==> foo
        _node = std::move(lhs._node);
        _priority = lhs._priority;
        _inverted = lhs._inverted;
        _resultSet = lhs._resultSet;
        addNodeCount(lhs);
        return;
    }
    if (lhs.isInvalid() && rhs.isInvalid()) {
        setInvalidConst();
        return;
    }
    if (lhs._inverted != _inverted) {
        lhs.invertNode();
    }
    if (rhs._inverted != _inverted) {
        rhs.invertNode();
    }
    if (lhs._priority < _priority) {
        lhs._node->setParentheses();
    }
    if (rhs._priority < _priority) {
        rhs._node->setParentheses();
    }
    if (_inverted) {
        _node.reset(new Or(std::move(lhs._node), std::move(rhs._node), "or"));
    } else {
        _node.reset(new And(std::move(lhs._node), std::move(rhs._node), "and"));
    }
    addNodeCount(lhs);
    addNodeCount(rhs);
}


void
SelectPruner::visitComparison(const Compare &expr)
{
    SelectPruner lhs(this);
    SelectPruner rhs(this);
    expr.getLeft().visit(lhs);
    expr.getRight().visit(rhs);
    _constVal = lhs._constVal && rhs._constVal;
    if (lhs.isInvalidVal() || rhs.isInvalidVal()) {
        _inverted = _wantInverted;
        _resultSet.add(Result::Invalid);
        setInvalidConst();
        return;
    }
    bool lhsNullVal = lhs.isNullVal();
    bool rhsNullVal = rhs.isNullVal();
    const Operator &op(getOperator(expr.getOperator()));
    _node.reset(new Compare(std::move(lhs._valueNode),
                            op,
                            std::move(rhs._valueNode),
                            expr.getBucketIdFactory()));
    _priority = ComparePriority;
    if (_constVal && (lhsNullVal || rhsNullVal)) {
        if (!lhsNullVal || !rhsNullVal) {
            // One null value
            _inverted = _wantInverted;
            _resultSet.add(Result::Invalid);
            setInvalidConst();
            return;
        }
        // Two null values
        resolveTernaryConst(_wantInverted);
        if (isInvalid()) {
            _resultSet.add(Result::Invalid);
        } else {
            _resultSet.add(isTrue() != _inverted ?
                           Result::True : Result::False);
        }
        return;
    }
    _resultSet.fill();  // should be less if const
    addNodeCount(lhs);
    addNodeCount(rhs);
}


void
SelectPruner::visitDocumentType(const DocType &expr)
{
    _constVal = true;
    bool res = expr.contains(_emptyDoc) == Result::True;
    if (_wantInverted) {
        _inverted = true;
        res = !res;
    }
    _node.reset(new Constant(res));
    _resultSet.add(res ? Result::True : Result::False);
    _priority = DocumentTypePriority;
}


void
SelectPruner::visitNotBranch(const Not &expr)
{
    _wantInverted = !_wantInverted;
    expr.getChild().visit(*this);
    _inverted = !_inverted;
    _wantInverted = !_wantInverted;
}


void
SelectPruner::visitOrBranch(const Or &expr)
{
    SelectPruner lhs(this);
    SelectPruner rhs(this);
    if (_wantInverted) {
        lhs._wantInverted = true;
        rhs._wantInverted = true;
    }
    expr.getLeft().visit(lhs);
    expr.getRight().visit(rhs);
    if (lhs.getFieldNodes() - lhs.getAttrFieldNodes() >
        rhs.getFieldNodes() - rhs.getAttrFieldNodes()) {
        lhs.swap(rhs);
    }
    ResultSet lhsSet(lhs._resultSet);
    ResultSet rhsSet(rhs._resultSet);
    if (lhs._inverted) {
        lhsSet = lhsSet.calcNot();
    }
    if (rhs._inverted) {
        rhsSet = rhsSet.calcNot();
    }
    _resultSet = lhsSet.calcOr(rhsSet);
    _priority = OrPriority;
    if (lhs._inverted && rhs._inverted) {
        // De Morgan's laws
        _inverted = true;
        _priority = AndPriority;
        _resultSet = _resultSet.calcNot();
    }
    _constVal = lhs._constVal && rhs._constVal;
    lhs.resolveTernaryConst(_inverted);
    rhs.resolveTernaryConst(_inverted);
    if (lhs.isTrue() || rhs.isTrue()) {
        // true or foo ==> true
        // foo or true ==> true
        setTernaryConst(!_inverted);
        return;
    }
    if (lhs.isFalse()) {
        if (rhs.isFalse()) {
            // false or false ==> false
            setTernaryConst(_inverted);
            return;
        }
        // false or foo ==> foo
        _node = std::move(rhs._node);
        _priority = rhs._priority;
        _inverted = rhs._inverted;
        _resultSet = rhs._resultSet;
        addNodeCount(rhs);
        return;
    } else if (rhs.isFalse()) {
        // foo or false ==> foo
        _node = std::move(lhs._node);
        _priority = lhs._priority;
        _inverted = lhs._inverted;
        _resultSet = lhs._resultSet;
        addNodeCount(lhs);
        return;
    }
    if (lhs.isInvalid() && rhs.isInvalid()) {
        setInvalidConst();
        return;
    }
    if (lhs._inverted != _inverted) {
        lhs.invertNode();
    }
    if (rhs._inverted != _inverted) {
        rhs.invertNode();
    }
    if (lhs._priority < _priority) {
        lhs._node->setParentheses();
    }
    if (rhs._priority < _priority) {
        rhs._node->setParentheses();
    }
    if (_inverted) {
        _node.reset(new And(std::move(lhs._node), std::move(rhs._node), "and"));
    } else {
        _node.reset(new Or(std::move(lhs._node), std::move(rhs._node), "or"));
    }
    addNodeCount(lhs);
    addNodeCount(rhs);
}


void
SelectPruner::visitArithmeticValueNode(const ArithmeticValueNode &expr)
{
    SelectPruner lhs(this);
    SelectPruner rhs(this);
    expr.getLeft().visit(lhs);
    expr.getRight().visit(rhs);
    if (lhs.isInvalidVal() || rhs.isInvalidVal()) {
        setInvalidVal();
        return;
    }
    setArithmeticValueNode(expr,
                           std::move(lhs._valueNode), lhs._priority, lhs._constVal,
                           std::move(rhs._valueNode), rhs._priority, rhs._constVal);
    addNodeCount(lhs);
    addNodeCount(rhs);
}


void
SelectPruner::visitFunctionValueNode(const FunctionValueNode &expr)
{
    expr.getChild().visit(*this);
    if (isInvalidVal()) {
        return; // Can shortcut evaluation when function argument is invalid
    }
    ValueNode::UP child(std::move(_valueNode));
    const vespalib::string &funcName(expr.getFunctionName());
    _valueNode.reset(new FunctionValueNode(funcName, std::move(child)));
    if (_priority < FuncPriority) {
        _valueNode->setParentheses();
    }
    _priority = FuncPriority;
}


void
SelectPruner::visitIdValueNode(const IdValueNode &expr)
{
    if (!_hasDocuments) {
        setInvalidVal();
        return;
    }
    CloningVisitor::visitIdValueNode(expr);
}


void
SelectPruner::visitFieldValueNode(const FieldValueNode &expr)
{
    if (_docType != expr.getDocType()) {
        setInvalidVal();
        return;
    }
    const document::DocumentType *docType = _repo.getDocumentType(_docType);
    bool complex = false; // Cannot handle attribute if complex expression
    vespalib::string name = SelectUtils::extractFieldName(expr, complex);
    try {
        std::unique_ptr<Field> fp(new Field(docType->getField(name)));
        if (!fp) {
            setInvalidVal();
            return;
        }
    } catch (FieldNotFoundException &) {
        setInvalidVal();
        return;
    }
    try {
        FieldPath path;
        docType->buildFieldPath(path, expr.getFieldName());
    } catch (vespalib::IllegalArgumentException &) {
        setInvalidVal();
        return;
    } catch (FieldNotFoundException &) {
        setInvalidVal();
        return;
    }
    _constVal = false;
    if (!_hasFields) {
        // If we're working on removed document sub db then we have no fields.
        _constVal = true;
        _valueNode.reset(new NullValueNode());
        _priority = NullValPriority;
        return;
    }
    
    _valueNode = expr.clone(); // Replace with different node type for attrs ?
    _valueNode->clearParentheses();
    bool svAttr = false;
    bool attrField = false;
    if (_amgr != nullptr) {
        AttributeGuard::UP ag(_amgr->getAttribute(name));
        if (ag->valid()) {
            attrField = true;
            auto av(ag->getSP());
            if (av->getCollectionType() == CollectionType::SINGLE && !complex) {
                svAttr = true;
            }
        }
    }
    if (!_hasDocuments && !svAttr) {
        setInvalidVal();
        return;
    }
    ++_fieldNodes;
    if (attrField) {
        ++_attrFieldNodes;
    }
    _priority = FieldValuePriority;
}


void
SelectPruner::invertNode()
{
    _resultSet = _resultSet.calcNot();
    if (isInvalid()) {
        _inverted = !_inverted;
        return;
    }
    if (_priority < NotPriority) {
        _node->setParentheses();
    }
    NodeUP node(std::move(_node));
    _node.reset(new Not(std::move(node), "not"));
    _priority = NotPriority;
    _inverted = !_inverted;
}


const Operator &
SelectPruner::getOperator(const Operator &op)
{
    if (!_wantInverted) {
        return op;
    }
    if (op == FunctionOperator::GT) {
        _inverted = true;
        return FunctionOperator::LEQ;
    }
    if (op == FunctionOperator::GEQ) {
        _inverted = true;
        return FunctionOperator::LT;
    }
    if (op == FunctionOperator::EQ) {
        _inverted = true;
        return FunctionOperator::NE;
    }
    if (op == FunctionOperator::LEQ) {
        _inverted = true;
        return FunctionOperator::GT;
    }
    if (op == FunctionOperator::LT) {
        _inverted = true;
        return FunctionOperator::GEQ;
    }
    if (op == FunctionOperator::NE) {
        _inverted = true;
        return FunctionOperator::EQ;
    }
    return op;
}


void
SelectPruner::addNodeCount(const SelectPruner &rhs)
{
    _fieldNodes += rhs._fieldNodes;
    _attrFieldNodes += rhs._attrFieldNodes;
}


void
SelectPruner::setInvalidVal()
{
    _constVal = true;
    _priority = InvalidValPriority;
    _valueNode.reset(new InvalidValueNode("invalidval"));
}


void
SelectPruner::setInvalidConst()
{
    _constVal = true;
    _priority = InvalidConstPriority;
    _node.reset(new InvalidConstant("invalid"));
}


void
SelectPruner::setTernaryConst(bool val)
{
    _constVal = true;
    _priority = ConstPriority;
    _node.reset(new Constant(val));
}

void
SelectPruner::resolveTernaryConst(bool wantInverted)
{
    if (!_constVal) {
        return;
    }
    const Result &res1(_node->contains(_emptyDoc).combineResults());
    const Result &res = _inverted == wantInverted ? res1 : !res1;
    if (res == Result::Invalid) {
        setInvalidConst();
    } else {
        setTernaryConst(res == Result::True);
        if (_inverted != wantInverted) {
            _resultSet = _resultSet.calcNot();
        }
        _inverted = wantInverted;
    }
}


bool
SelectPruner::isFalse() const
{
    if (!_constVal) {
        return false;
    }
    Constant *c(dynamic_cast<Constant *>(_node.get()));
    if (c != nullptr) {
        return _inverted == c->getConstantValue();
    }
    InvalidConstant *ic(dynamic_cast<InvalidConstant *>(_node.get()));
    if (ic != nullptr) {
        return false;
    }
    const Result &res(_node->contains(_emptyDoc).combineResults());
    return _inverted ? res == Result::True : res == Result::False;
}


bool
SelectPruner::isTrue() const
{
    if (!_constVal) {
        return false;
    }
    Constant *c(dynamic_cast<Constant *>(_node.get()));
    if (c != nullptr) {
        return _inverted != c->getConstantValue();
    }
    InvalidConstant *ic(dynamic_cast<InvalidConstant *>(_node.get()));
    if (ic != nullptr) {
        return false;
    }
    const Result &res(_node->contains(_emptyDoc).combineResults());
    return _inverted ? res == Result::False : res == Result::True;
}


bool
SelectPruner::isInvalid() const
{
    if (!_constVal) {
        return false;
    }
    Constant *c(dynamic_cast<Constant *>(_node.get()));
    if (c != nullptr) {
        return false;
    }
    InvalidConstant *ic(dynamic_cast<InvalidConstant *>(_node.get()));
    if (ic != nullptr) {
        return true;
    }
    const Result &res(_node->contains(_emptyDoc).combineResults());
    return res == Result::Invalid;
}


bool
SelectPruner::isInvalidVal() const
{
    if (!_constVal) {
        return false;
    }
    InvalidValueNode *iv(dynamic_cast<InvalidValueNode *>(_valueNode.get()));
    return iv != nullptr;
}


bool
SelectPruner::isNullVal() const
{
    if (!_constVal) {
        return false;
    }
    NullValueNode *nv(dynamic_cast<NullValueNode *>(_valueNode.get()));
    return nv != nullptr;
}


bool
SelectPruner::isConst() const
{
    return _constVal;
}


void
SelectPruner::trace(std::ostream &t)
{
    _node->trace(_emptyDoc, t);
}


void
SelectPruner::process(const Node &node)
{
    node.visit(*this);
    resolveTernaryConst(false);
    if (_inverted) {
        invertNode();
    }
}


void
SelectPruner::swap(SelectPruner &rhs)
{
    CloningVisitor::swap(rhs);
    std::swap(_inverted, rhs._inverted);
    std::swap(_wantInverted, rhs._wantInverted);
    std::swap(_attrFieldNodes, rhs._attrFieldNodes);
}


} // namespace proton
