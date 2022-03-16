// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::FloatFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype FLOAT.
 */
#pragma once

#include "numericfieldvalue.h"
#include <vespa/document/datatype/datatype.h>

namespace document {

class FloatFieldValue final : public NumericFieldValue<float> {
public:
    typedef float Number;

    FloatFieldValue(Number value = 0) : NumericFieldValue<Number>(Type::FLOAT, value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    const DataType *getDataType() const override { return DataType::FLOAT; }
    FloatFieldValue* clone() const override { return new FloatFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;
    static std::unique_ptr<FloatFieldValue> make(Number value = 0) { return std::make_unique<FloatFieldValue>(value); }
};

} // document

