// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ShortFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype SHORT.
 */
#pragma once

#include "numericfieldvalue.h"
#include <vespa/document/datatype/datatype.h>

namespace document {

class ShortFieldValue final : public NumericFieldValue<int16_t> {
public:
    typedef std::unique_ptr<ShortFieldValue> UP;
    typedef int16_t Number;

    ShortFieldValue(Number value = 0)
        : NumericFieldValue<Number>(Type::SHORT, value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    const DataType *getDataType() const override { return DataType::SHORT; }
    ShortFieldValue* clone() const override { return new ShortFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;
    static std::unique_ptr<ShortFieldValue> make(int16_t value = 0) { return std::make_unique<ShortFieldValue>(value); }
};

} // document

