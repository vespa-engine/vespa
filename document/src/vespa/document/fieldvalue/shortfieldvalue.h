// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ShortFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype SHORT.
 */
#pragma once

#include <vespa/document/datatype/numericdatatype.h>
#include <vespa/document/fieldvalue/numericfieldvalue.h>

namespace document {

class ShortFieldValue : public NumericFieldValue<int16_t> {
public:
    typedef std::unique_ptr<ShortFieldValue> UP;
    typedef int16_t Number;

    ShortFieldValue(Number value = 0)
        : NumericFieldValue<Number>(value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    virtual const DataType *getDataType() const { return DataType::SHORT; }

    virtual ShortFieldValue* clone() const { return new ShortFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;

    DECLARE_IDENTIFIABLE(ShortFieldValue);

};

} // document

