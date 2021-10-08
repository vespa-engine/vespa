// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ByteFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype BYTE.
 */
#pragma once

#include "numericfieldvalue.h"
#include <vespa/document/datatype/numericdatatype.h>

namespace document {

class ByteFieldValue : public NumericFieldValue<int8_t> {
public:
    typedef std::unique_ptr<ByteFieldValue> UP;
    typedef int8_t Number;

    ByteFieldValue(Number value = 0)
        : NumericFieldValue<Number>(value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }
    const DataType *getDataType() const override { return DataType::BYTE; }
    ByteFieldValue* clone() const override { return new ByteFieldValue(*this); }
    using NumericFieldValue<Number>::operator=;

    DECLARE_IDENTIFIABLE(ByteFieldValue);

};

} // document

