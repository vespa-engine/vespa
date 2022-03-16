// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ByteFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype BYTE.
 */
#pragma once

#include "numericfieldvalue.h"
#include <vespa/document/datatype/datatype.h>

namespace document {

class ByteFieldValue final : public NumericFieldValue<int8_t> {
public:
    typedef int8_t Number;

    ByteFieldValue(Number value = 0)
        : NumericFieldValue<Number>(Type::BYTE, value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }
    const DataType *getDataType() const override { return DataType::BYTE; }
    ByteFieldValue* clone() const override { return new ByteFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;
    static std::unique_ptr<ByteFieldValue> make(Number value=0) { return std::make_unique<ByteFieldValue>(value); }
};

} // document

