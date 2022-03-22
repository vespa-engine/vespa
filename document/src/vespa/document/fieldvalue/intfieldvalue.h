// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::IntFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype INT.
 */
#pragma once

#include "numericfieldvalue.h"
#include <vespa/document/datatype/datatype.h>

namespace document {

class IntFieldValue final : public NumericFieldValue<int32_t> {
public:
    typedef int32_t Number;

    IntFieldValue(Number value = 0) : NumericFieldValue<Number>(Type::INT, value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    const DataType *getDataType() const override { return DataType::INT; }
    IntFieldValue* clone() const override { return new IntFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;
    static std::unique_ptr<IntFieldValue> make(Number value=0) { return std::make_unique<IntFieldValue>(value); }
};

} // document

