// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryfieldconverter.h"
#include "annotation_converter.h"
#include "check_undefined_value_visitor.h"
#include "searchdatatype.h"
#include "slime_filler.h"
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/boolfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/annotationreferencefieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/smart_buffer.h>

using document::AnnotationReferenceFieldValue;
using document::ArrayFieldValue;
using document::BoolFieldValue;
using document::ByteFieldValue;
using document::Document;
using document::DoubleFieldValue;
using document::FieldValue;
using document::ConstFieldValueVisitor;
using document::FloatFieldValue;
using document::IntFieldValue;
using document::LongFieldValue;
using document::MapFieldValue;
using document::PredicateFieldValue;
using document::RawFieldValue;
using document::ShortFieldValue;
using document::StringFieldValue;
using document::StructFieldValue;
using document::WeightedSetFieldValue;
using document::TensorFieldValue;
using document::ReferenceFieldValue;

namespace search::docsummary {

namespace {

struct FieldValueConverter {
    virtual FieldValue::UP convert(const FieldValue &input) = 0;
    virtual ~FieldValueConverter() = default;
};


class SummaryFieldValueConverter : protected ConstFieldValueVisitor
{
    vespalib::asciistream _str;
    bool                 _tokenize;
    FieldValue::UP       _field_value;
    FieldValueConverter &_structuredFieldConverter;

    template <typename T>
    void visitPrimitive(const T &t) {
        _field_value.reset(t.clone());
    }
    void visit(const IntFieldValue &value) override { visitPrimitive(value); }
    void visit(const LongFieldValue &value) override { visitPrimitive(value); }
    void visit(const ShortFieldValue &value) override { visitPrimitive(value); }
    void visit(const BoolFieldValue &value) override { visitPrimitive(value); }
    void visit(const ByteFieldValue &value) override {
        int8_t signedValue = value.getAsByte();
        _field_value = std::make_unique<ShortFieldValue>(signedValue);
    }
    void visit(const DoubleFieldValue &value) override { visitPrimitive(value); }
    void visit(const FloatFieldValue &value) override { visitPrimitive(value); }

    void visit(const StringFieldValue &value) override {
        if (_tokenize) {
            AnnotationConverter converter(value.getValue(), _str);
            converter.handleIndexingTerms(value);
        } else {
            _str << value.getValue();
        }
    }

    void visit(const AnnotationReferenceFieldValue & v ) override {
        _field_value = _structuredFieldConverter.convert(v);
    }
    void visit(const Document & v) override {
        _field_value = _structuredFieldConverter.convert(v);
    }

    void visit(const PredicateFieldValue &value) override {
        _str << value.toString();
    }

    void visit(const RawFieldValue &value) override {
        visitPrimitive(value);
    }

    void visit(const ArrayFieldValue &value) override {
        if (value.size() > 0) {
            _field_value = _structuredFieldConverter.convert(value);
        } // else: implicit empty string
    }

    void visit(const MapFieldValue & value) override {
        if (value.size() > 0) {
            _field_value = _structuredFieldConverter.convert(value);
        } // else: implicit empty string
    }

    void visit(const StructFieldValue &value) override {
        if (*value.getDataType() == *SearchDataType::URI) {
            FieldValue::UP uriAllValue = value.getValue("all");
            if (uriAllValue && uriAllValue->isA(FieldValue::Type::STRING)) {
                uriAllValue->accept(*this);
                return;
            }
        }
        _field_value = _structuredFieldConverter.convert(value);
    }

    void visit(const WeightedSetFieldValue &value) override {
        if (value.size() > 0) {
            _field_value = _structuredFieldConverter.convert(value);
        } // else: implicit empty string
    }

    void visit(const TensorFieldValue &value) override {
        visitPrimitive(value);
    }

    void visit(const ReferenceFieldValue& value) override {
        if (value.hasValidDocumentId()) {
            _str << value.getDocumentId().toString();
        } // else: implicit empty string
    }

public:
    SummaryFieldValueConverter(bool tokenize, FieldValueConverter &subConverter);
    ~SummaryFieldValueConverter() override;

    FieldValue::UP convert(const FieldValue &input) {
        input.accept(*this);
        if (_field_value.get()) {
            return std::move(_field_value);
        }
        return StringFieldValue::make(_str.str());
    }
};

SummaryFieldValueConverter::SummaryFieldValueConverter(bool tokenize, FieldValueConverter &subConverter)
    : _str(), _tokenize(tokenize),
      _structuredFieldConverter(subConverter)
{}
SummaryFieldValueConverter::~SummaryFieldValueConverter() = default;

using namespace vespalib::slime::convenience;

class SlimeConverter : public FieldValueConverter {
private:
    bool _tokenize;
    const std::vector<uint32_t>* _matching_elems;

public:
    explicit SlimeConverter(bool tokenize)
        : _tokenize(tokenize),
          _matching_elems()
    {}

    SlimeConverter(bool tokenize, const std::vector<uint32_t>& matching_elems)
            : _tokenize(tokenize),
              _matching_elems(&matching_elems)
    {}

    FieldValue::UP convert(const FieldValue &input) override {
        vespalib::Slime slime;
        SlimeInserter inserter(slime);
        SlimeFiller visitor(inserter, _tokenize, _matching_elems);
        input.accept(visitor);
        vespalib::SmartBuffer buffer(4_Ki);
        vespalib::slime::BinaryFormat::encode(slime, buffer);
        vespalib::Memory mem = buffer.obtain();
        return std::make_unique<RawFieldValue>(mem.data, mem.size);
    }
};


}  // namespace

FieldValue::UP
SummaryFieldConverter::convertSummaryField(bool markup,
                                           const FieldValue &value)
{
    SlimeConverter subConv(markup);
    return SummaryFieldValueConverter(markup, subConv).convert(value);
}

void
SummaryFieldConverter::insert_summary_field(const FieldValue& value, vespalib::slime::Inserter& inserter)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, false);
        value.accept(visitor);
    }
}

void
SummaryFieldConverter::insert_summary_field_with_filter(const FieldValue& value, vespalib::slime::Inserter& inserter, const std::vector<uint32_t>& matching_elems)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, false, &matching_elems);
        value.accept(visitor);
    }
}

void
SummaryFieldConverter::insert_juniper_field(const document::FieldValue& value, vespalib::slime::Inserter& inserter, bool tokenize, IJuniperConverter& converter)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, tokenize, &converter);
        value.accept(visitor);
    }
}

}
