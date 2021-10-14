// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::FloatFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype FLOAT.
 */
#pragma once

#include <vespa/document/datatype/numericdatatype.h>
#include <vespa/document/fieldvalue/numericfieldvalue.h>

namespace document {

class FloatFieldValue : public NumericFieldValue<float> {
public:
    typedef std::unique_ptr<FloatFieldValue> UP;
    typedef float Number;

    FloatFieldValue(Number value = 0) : NumericFieldValue<Number>(value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    const DataType *getDataType() const override { return DataType::FLOAT; }
    FloatFieldValue* clone() const override { return new FloatFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;

    DECLARE_IDENTIFIABLE(FloatFieldValue);

};

} // document

