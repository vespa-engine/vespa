// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::LongFieldValue
 * \ingroup fieldvalue
 *
 * \brief Wrapper for field values of datatype LONG.
 */
#pragma once

#include <vespa/document/datatype/numericdatatype.h>
#include <vespa/document/fieldvalue/numericfieldvalue.h>

namespace document {

class LongFieldValue : public NumericFieldValue<int64_t> {
public:
    typedef std::unique_ptr<LongFieldValue> UP;
    typedef int64_t Number;

    LongFieldValue(Number value = 0) : NumericFieldValue<Number>(value) {}

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    const DataType *getDataType() const override { return DataType::LONG; }
    LongFieldValue* clone() const override { return new LongFieldValue(*this); }

    using NumericFieldValue<Number>::operator=;

    DECLARE_IDENTIFIABLE(LongFieldValue);

};

} // document

