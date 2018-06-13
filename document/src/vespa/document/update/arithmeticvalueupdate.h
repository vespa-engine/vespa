// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::ArithmeticValueUpdate
 * @ingroup document
 *
 * @brief Represent an update that specifies an arithmetic operation that is to
 *        be applied to the weight of a field value.
 */
#pragma once

#include "valueupdate.h"

namespace document {

class ArithmeticValueUpdate : public ValueUpdate {
public:
    /** Declare all types of arithmetic value updates. */
    enum Operator {
        Add = 0, // Add the operand to the field value.
        Div,     // Divide the field value with the operand.
        Mul,     // Multiply the field value with the operand.
        Sub,     // Subtract the operand from the field value.
        MAX_NUM_OPERATORS
    };

private:
    Operator _operator; // The operator of the arithmetic operation.
    double _operand; // The operand of the arithmetic operation.

    // Used by ValueUpdate's static factory function
    // Private because it generates an invalid object.
    friend class ValueUpdate;
    ArithmeticValueUpdate()
        : ValueUpdate(),
          _operator(MAX_NUM_OPERATORS),
          _operand(0.0) {}

    ACCEPT_UPDATE_VISITOR;
public:
    typedef std::unique_ptr<ArithmeticValueUpdate> UP;

    /**
     * The default constructor requires initial values for all member variables.
     *
     * @param opt The operator of this arithmetic update.
     * @param opn The operand for the operation.
     */
    ArithmeticValueUpdate(Operator opt, double opn)
        : ValueUpdate(),
          _operator(opt),
          _operand(opn) {}

    ArithmeticValueUpdate(const ArithmeticValueUpdate& update)
        : ValueUpdate(update),
          _operator(update._operator),
          _operand(update._operand) {}

    bool operator==(const ValueUpdate& other) const override;

    /** @return the operator of this arithmetic update. */
    Operator getOperator() const { return _operator; }

    /** @return the operand of this arithmetic update. */
    double getOperand() const { return _operand; }

    /**
     * Apply the contained operation on the given double.
     *
     * @param value The value to modify.
     * @return The modified value.
     */
    double applyTo(double value) const;

    /**
     * Apply the contained operation on the given string.
     *
     * @param value The value to modify.
     * @return The modified value.
     */
    std::string applyTo(const std::string& value) const;

    /**
     * Apply the contained operation on the given long.
     *
     * @param value The value to modify.
     * @return The modified value.
     */
    long applyTo(int64_t value) const;

    // ValueUpdate implementation
    void checkCompatibility(const Field& field) const override;
    bool applyTo(FieldValue& value) const override;
    void printXml(XmlOutputStream& xos) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void deserialize(const DocumentTypeRepo& repo, const DataType& type,
                     nbostream & buffer, uint16_t version) override;
    ArithmeticValueUpdate* clone() const override { return new ArithmeticValueUpdate(*this); }

    DECLARE_IDENTIFIABLE(ArithmeticValueUpdate);

};

} // document

