// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::Visitor
 * @ingroup select
 *
 * @brief Visitor class for going through the select tree.
 *
 * @author H�kon Humberset
 * @date 2005-06-07
 * @version $Id$
 */

#pragma once

namespace document::select {

class And;
class Compare;
class Constant;
class DocType;
class Not;
class Or;
class ArithmeticValueNode;
class FunctionValueNode;
class IdValueNode;
class InvalidConstant;
class FieldValueNode;
class FloatValueNode;
class IntegerValueNode;
class BoolValueNode;
class CurrentTimeValueNode;
class StringValueNode;
class NullValueNode;
class InvalidValueNode;
class VariableValueNode;

class Visitor {
public:
    virtual ~Visitor() = default;

    virtual void visitAndBranch(const And &) = 0;
    virtual void visitComparison(const Compare &) = 0;
    virtual void visitConstant(const Constant &) = 0;
    virtual void visitInvalidConstant(const InvalidConstant &) = 0;
    virtual void visitDocumentType(const DocType &) = 0;
    virtual void visitNotBranch(const Not &) = 0;
    virtual void visitOrBranch(const Or &) = 0;
    virtual void visitArithmeticValueNode(const ArithmeticValueNode &) = 0;
    virtual void visitFunctionValueNode(const FunctionValueNode &) = 0;
    virtual void visitIdValueNode(const IdValueNode &) = 0;
    virtual void visitFieldValueNode(const FieldValueNode &) = 0;
    virtual void visitFloatValueNode(const FloatValueNode &) = 0;
    virtual void visitVariableValueNode(const VariableValueNode &) = 0;
    virtual void visitIntegerValueNode(const IntegerValueNode &) = 0;
    virtual void visitBoolValueNode(const BoolValueNode&) = 0;
    virtual void visitCurrentTimeValueNode(const CurrentTimeValueNode &) = 0;
    virtual void visitStringValueNode(const StringValueNode &) = 0;
    virtual void visitNullValueNode(const NullValueNode &) = 0;
    virtual void visitInvalidValueNode(const InvalidValueNode &) = 0;
};

}
