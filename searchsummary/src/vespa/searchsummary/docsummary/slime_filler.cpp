// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slime_filler.h"
#include "check_undefined_value_visitor.h"
#include "i_juniper_converter.h"
#include "i_string_field_converter.h"
#include "resultconfig.h"
#include "slime_filler_filter.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/boolfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <cassert>

using document::AnnotationReferenceFieldValue;
using document::ArrayFieldValue;
using document::BoolFieldValue;
using document::ByteFieldValue;
using document::Document;
using document::DoubleFieldValue;
using document::FieldValue;
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
using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;
using vespalib::slime::ObjectInserter;
using vespalib::slime::ObjectSymbolInserter;
using vespalib::slime::Symbol;
using vespalib::Memory;
using vespalib::asciistream;

namespace search::docsummary {

namespace {

class MapFieldValueInserter {
private:
    Cursor& _array;
    Symbol _key_sym;
    Symbol _val_sym;
    SlimeFillerFilter::Iterator _filter;

public:
    MapFieldValueInserter(Inserter& parent_inserter, SlimeFillerFilter::Iterator filter)
        : _array(parent_inserter.insertArray()),
          _key_sym(_array.resolve("key")),
          _val_sym(_array.resolve("value")),
          _filter(filter)
    {
    }
    void insert_entry(const FieldValue& key, const FieldValue& value) {
        Cursor& c = _array.addObject();
        ObjectSymbolInserter ki(c, _key_sym);
        SlimeFiller key_conv(ki);

        key.accept(key_conv);
        if (_filter.should_render()) {
            ObjectSymbolInserter vi(c, _val_sym);
            SlimeFiller val_conv(vi, nullptr, _filter);
            value.accept(val_conv);
        }
    }
};

}

SlimeFiller::SlimeFiller(Inserter& inserter)
    : _inserter(inserter),
      _matching_elems(nullptr),
      _string_converter(nullptr),
      _filter(SlimeFillerFilter::all())
{
}

SlimeFiller::SlimeFiller(Inserter& inserter, const std::vector<uint32_t>* matching_elems)
    : _inserter(inserter),
      _matching_elems(matching_elems),
      _string_converter(nullptr),
      _filter(SlimeFillerFilter::all())
{
}

SlimeFiller::SlimeFiller(Inserter& inserter, IStringFieldConverter* string_converter, SlimeFillerFilter::Iterator filter)
    : _inserter(inserter),
      _matching_elems(nullptr),
      _string_converter(string_converter),
      _filter(filter)
{
}

SlimeFiller::~SlimeFiller() = default;

void
SlimeFiller::visit(const AnnotationReferenceFieldValue& v)
{
    (void)v;
    Cursor& c = _inserter.insertObject();
    Memory key("error");
    Memory val("cannot convert from annotation reference field");
    c.setString(key, val);
}

void
SlimeFiller::visit(const Document& v)
{
    (void)v;
    Cursor& c = _inserter.insertObject();
    Memory key("error");
    Memory val("cannot convert from field of type document");
    c.setString(key, val);
}

void
SlimeFiller::visit(const MapFieldValue& v)
{
    if (empty_or_empty_after_filtering(v)) {
        return;
    }
    MapFieldValueInserter map_inserter(_inserter, _filter.check_field("value"));
    if (filter_matching_elements()) {
        assert(v.has_no_erased_keys());
        for (uint32_t id_to_keep : (*_matching_elems)) {
            auto entry = v[id_to_keep];
            map_inserter.insert_entry(*entry.first, *entry.second);
        }
    } else {
        for (const auto& entry : v) {
            map_inserter.insert_entry(*entry.first, *entry.second);
        }
    }
}

void
SlimeFiller::visit(const ArrayFieldValue& value)
{
    if (empty_or_empty_after_filtering(value)) {
        return;
    }
    Cursor& a = _inserter.insertArray();
    ArrayInserter ai(a);
    SlimeFiller conv(ai, _string_converter, _filter);
    if (filter_matching_elements()) {
        for (uint32_t id_to_keep : (*_matching_elems)) {
            value[id_to_keep].accept(conv);
        }
    } else {
        for (const FieldValue& fv : value) {
            fv.accept(conv);
        }
    }
}

void
SlimeFiller::visit(const StringFieldValue& value)
{
    if (_string_converter != nullptr) {
        _string_converter->convert(value, _inserter);
    } else {
        _inserter.insertString(Memory(value.getValueRef()));
    }
}

void
SlimeFiller::visit(const IntFieldValue& value)
{
    int32_t v = value.getValue();
    _inserter.insertLong(v);
}

void
SlimeFiller::visit(const LongFieldValue& value)
{
    int64_t v = value.getValue();
    _inserter.insertLong(v);
}

void
SlimeFiller::visit(const ShortFieldValue& value)
{
    int16_t v = value.getValue();
    _inserter.insertLong(v);
}

void
SlimeFiller::visit(const ByteFieldValue& value)
{
    int8_t v = value.getAsByte();
    _inserter.insertLong(v);
}

void
SlimeFiller::visit(const BoolFieldValue& value)
{
    bool v = value.getValue();
    _inserter.insertBool(v);
}

void
SlimeFiller::visit(const DoubleFieldValue& value)
{
    double v = value.getValue();
    _inserter.insertDouble(v);
}

void
SlimeFiller::visit(const FloatFieldValue& value)
{
    float v = value.getValue();
    _inserter.insertDouble(v);
}

void
SlimeFiller::visit(const PredicateFieldValue& value)
{
    _inserter.insertString(value.toString());
}

void
SlimeFiller::visit(const RawFieldValue& value)
{
    std::pair<const char *, size_t> buf = value.getAsRaw();
    _inserter.insertData(Memory(buf.first, buf.second));
}

void
SlimeFiller::visit(const StructFieldValue& value)
{
    if (value.getDataType() == &document::PositionDataType::getInstance()
        && ResultConfig::wantedV8geoPositions())
    {
        auto xv = value.getValue("x");
        auto yv = value.getValue("y");
        if (xv && yv) {
            Cursor& c = _inserter.insertObject();
            c.setDouble("lat", double(yv->getAsInt()) / 1.0e6);
            c.setDouble("lng", double(xv->getAsInt()) / 1.0e6);
            return;
        }
    }
    Cursor& c = _inserter.insertObject();
    for (StructFieldValue::const_iterator itr = value.begin(); itr != value.end(); ++itr) {
        auto& name = itr.field().getName();
        auto sub_filter = _filter.check_field(name);
        if (sub_filter.should_render()) {
            Memory keymem(name);
            ObjectInserter vi(c, keymem);
            SlimeFiller conv(vi, nullptr, sub_filter);
            FieldValue::UP nextValue(value.getValue(itr.field()));
            (*nextValue).accept(conv);
        }
    }
}

void
SlimeFiller::visit(const WeightedSetFieldValue& value)
{
    if (empty_or_empty_after_filtering(value)) {
        return;
    }
    Cursor& a = _inserter.insertArray();
    Symbol isym = a.resolve("item");
    Symbol wsym = a.resolve("weight");
    using matching_elements_iterator_type = std::vector<uint32_t>::const_iterator;
    matching_elements_iterator_type matching_elements_itr;
    matching_elements_iterator_type matching_elements_itr_end;
    if (filter_matching_elements()) {
        matching_elements_itr = _matching_elems->begin();
        matching_elements_itr_end = _matching_elems->end();
    }
    uint32_t idx = 0;
    for (const auto& entry : value) {
        if (filter_matching_elements()) {
            if (matching_elements_itr == matching_elements_itr_end ||
                idx < *matching_elements_itr) {
                ++idx;
                continue;
            }
            ++matching_elements_itr;
        }
        Cursor& o = a.addObject();
        ObjectSymbolInserter ki(o, isym);
        SlimeFiller conv(ki);
        entry.first->accept(conv);
        int weight = static_cast<const IntFieldValue&>(*entry.second).getValue();
        o.setLong(wsym, weight);
        ++idx;
    }
}

void
SlimeFiller::visit(const TensorFieldValue& value)
{
    const auto& tensor = value.getAsTensorPtr();
    vespalib::nbostream s;
    if (tensor) {
        encode_value(*tensor, s);
    }
    _inserter.insertData(vespalib::Memory(s.peek(), s.size()));
}

void
SlimeFiller::visit(const ReferenceFieldValue& value)
{
    _inserter.insertString(Memory(value.hasValidDocumentId()
                                  ? value.getDocumentId().toString()
                                  : vespalib::string()));
}

void
SlimeFiller::insert_summary_field(const FieldValue& value, vespalib::slime::Inserter& inserter)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter);
        value.accept(visitor);
    }
}

void
SlimeFiller::insert_summary_field_with_filter(const FieldValue& value, vespalib::slime::Inserter& inserter, const std::vector<uint32_t>& matching_elems)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, &matching_elems);
        value.accept(visitor);
    }
}

void
SlimeFiller::insert_summary_field_with_field_filter(const document::FieldValue& value, vespalib::slime::Inserter& inserter, const SlimeFillerFilter* filter)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, nullptr, (filter != nullptr) ? filter->begin() : SlimeFillerFilter::all());
        value.accept(visitor);
    }
}

void
SlimeFiller::insert_juniper_field(const document::FieldValue& value, vespalib::slime::Inserter& inserter, IStringFieldConverter& converter)
{
    CheckUndefinedValueVisitor check_undefined;
    value.accept(check_undefined);
    if (!check_undefined.is_undefined()) {
        SlimeFiller visitor(inserter, &converter, SlimeFillerFilter::all());
        value.accept(visitor);
    }
}

}
