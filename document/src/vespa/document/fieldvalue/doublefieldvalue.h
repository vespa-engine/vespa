// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::DoubleFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype DOUBLE.
 */
#pragma once

#include <vespa/document/datatype/numericdatatype.h>
#include <vespa/document/fieldvalue/numericfieldvalue.h>

namespace document {

class DoubleFieldValue : public NumericFieldValue<double> {
public:
    typedef std::unique_ptr<DoubleFieldValue> UP;
    typedef double Number;

    DoubleFieldValue(Number value = 0) : NumericFieldValue<Number>(value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    const DataType *getDataType() const override { return DataType::DOUBLE; }
    DoubleFieldValue* clone() const override { return new DoubleFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;

    DECLARE_IDENTIFIABLE(DoubleFieldValue);

};

} // document

