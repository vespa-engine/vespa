// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::DoubleFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype DOUBLE.
 */
#pragma once

#include "numericfieldvalue.h"
#include <vespa/document/datatype/datatype.h>

namespace document {

class DoubleFieldValue final : public NumericFieldValue<double> {
public:
    typedef double Number;

    DoubleFieldValue(Number value = 0) : NumericFieldValue<Number>(Type::DOUBLE, value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    const DataType *getDataType() const override { return DataType::DOUBLE; }
    DoubleFieldValue* clone() const override { return new DoubleFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;
    static std::unique_ptr<DoubleFieldValue> make(Number value=0) { return std::make_unique<DoubleFieldValue>(value); }
};

} // document

