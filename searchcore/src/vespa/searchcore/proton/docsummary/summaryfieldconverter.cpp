// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryfieldconverter.h"
#include "linguisticsannotation.h"
#include "searchdatatype.h"
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spannode.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/annotation/spantreevisitor.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/weightedsetdatatype.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/fieldvaluevisitor.h>
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
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/util/url.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/jsonwriter.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/convenience.h>
#include <vespa/vespalib/data/slime/binary_format.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/eval/tensor/serialization/slime_binary_format.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/vespalib/util/exceptions.h>


using document::AlternateSpanList;
using document::Annotation;
using document::AnnotationReferenceFieldValue;
using document::ArrayDataType;
using document::ArrayFieldValue;
using document::ByteFieldValue;
using document::DataType;
using document::Document;
using document::DocumentType;
using document::DoubleFieldValue;
using document::FieldValue;
using document::FixedTypeRepo;
using document::ConstFieldValueVisitor;
using document::FloatFieldValue;
using document::IntFieldValue;
using document::LongFieldValue;
using document::MapFieldValue;
using document::PredicateFieldValue;
using document::RawFieldValue;
using document::ShortFieldValue;
using document::Span;
using document::SpanList;
using document::SimpleSpanList;
using document::SpanNode;
using document::SpanTree;
using document::SpanTreeVisitor;
using document::StringFieldValue;
using document::StructFieldValue;
using document::WeightedSetDataType;
using document::WeightedSetFieldValue;
using document::TensorFieldValue;
using document::ReferenceFieldValue;
using search::index::Schema;
using search::util::URL;
using std::make_pair;
using std::pair;
using std::vector;
using vespalib::JSONWriter;
using vespalib::asciistream;
using vespalib::geo::ZCurve;
using vespalib::make_string;
using vespalib::string;
using vespalib::stringref;

namespace proton {

namespace {
string getSpanString(const string &s, const Span &span) {
    return string(&s[span.from()], &s[span.from() + span.length()]);
}

struct SpanFinder : SpanTreeVisitor {
    int32_t begin_pos;
    int32_t end_pos;

    SpanFinder() : begin_pos(0x7fffffff), end_pos(-1) {}
    Span span() { return Span(begin_pos, end_pos - begin_pos); }

    void visit(const Span &node) override {
        begin_pos = std::min(begin_pos, node.from());
        end_pos = std::max(end_pos, node.from() + node.length());
    }
    void visit(const SpanList &node) override {
        for (const auto & span_ : node) {
            span_->accept(*this);
        }
    }
    void visit(const SimpleSpanList &node) override {
        for (const auto & span_ : node) {
            span_.accept(*this);
        }
    }
    void visit(const AlternateSpanList &node) override {
        for (size_t i = 0; i < node.getNumSubtrees(); ++i) {
            visit(node.getSubtree(i));
        }
    }
};

Span getSpan(const SpanNode &span_node) {
    SpanFinder finder;
    span_node.accept(finder);
    return finder.span();
}

// Extract the FieldValues from all TERM annotations. For each span
// with such annotations, the Handler is invoked with a set of
// iterators over the FieldValues for that span.
template <typename Handler>
void handleIndexingTerms(Handler &handler, const StringFieldValue &value) {
        StringFieldValue::SpanTrees trees = value.getSpanTrees();
    const SpanTree *tree = StringFieldValue::findTree(trees, linguistics::SPANTREE_NAME);
    typedef pair<Span, const FieldValue *> SpanTerm;
    typedef vector<SpanTerm> SpanTermVector;
    if (!tree) {
        // Treat a string without annotations as a single span.
        SpanTerm str(Span(0, handler.text.size()),
                     static_cast<const FieldValue*>(0));
        handler.handleAnnotations(str.first, &str, &str + 1);
        return;
    }
    SpanTermVector terms;
    for (const Annotation & annotation : *tree) {
        // For now, skip any composite spans.
        const Span *span = dynamic_cast<const Span*>(annotation.getSpanNode());
        if ((span != nullptr) && annotation.valid() &&
            (annotation.getType() == *linguistics::TERM)) {
            terms.push_back(make_pair(getSpan(*span),
                                      annotation.getFieldValue()));
        }
    }
    sort(terms.begin(), terms.end());
    SpanTermVector::const_iterator it = terms.begin();
    SpanTermVector::const_iterator ite = terms.end();
    int32_t endPos = 0;
    for (; it != ite; ) {
        SpanTermVector::const_iterator it_begin = it;
        if (it_begin->first.from() >  endPos) {
            Span tmpSpan(endPos, it_begin->first.from() - endPos);
            handler.handleAnnotations(tmpSpan, it, it);
            endPos = it_begin->first.from();
        }
        for (; it != ite && it->first == it_begin->first; ++it);
        handler.handleAnnotations(it_begin->first, it_begin, it);
        endPos = it_begin->first.from() + it_begin->first.length();
    }
    int32_t wantEndPos = handler.text.size();
    if (endPos < wantEndPos) {
        Span tmpSpan(endPos, wantEndPos - endPos);
        handler.handleAnnotations(tmpSpan, ite, ite);
    }
}

const StringFieldValue &ensureStringFieldValue(const FieldValue &value) __attribute__((noinline));

const StringFieldValue &ensureStringFieldValue(const FieldValue &value) {
    if (!value.inherits(IDENTIFIABLE_CLASSID(StringFieldValue))) {
        throw vespalib::IllegalArgumentException("Illegal field type. " + value.toString(), VESPA_STRLOC);
    }
    return static_cast<const StringFieldValue &>(value);
}

struct FieldValueConverter {
    virtual FieldValue::UP convert(const FieldValue &input) = 0;
    virtual ~FieldValueConverter() {}
};


struct SummaryHandler {
    const string text;
    asciistream &out;

    SummaryHandler(const string &s, asciistream &stream)
        : text(s), out(stream) {}

    template <typename ForwardIt>
    void handleAnnotations(const Span &span, ForwardIt it, ForwardIt last) {
        int annCnt = (last - it);
        if (annCnt > 1 || (annCnt == 1 && it->second)) {
            annotateSpans(span, it, last);
        } else {
            out << getSpanString(text, span) << '\037';
        }
    }

    template <typename ForwardIt>
    void annotateSpans(const Span &span, ForwardIt it, ForwardIt last) {
        out << "\357\277\271"  // ANCHOR
            << (getSpanString(text, span))
            << "\357\277\272"; // SEPARATOR
        while (it != last) {
            if (it->second) {
                out << ensureStringFieldValue(*it->second).getValue();
            } else {
                out << getSpanString(text, span);
            }
            if (++it != last) {
                out << " ";
            }
        }
        out << "\357\277\273"  // TERMINATOR
            << "\037";
    }
};



class JsonFiller : public ConstFieldValueVisitor {
    JSONWriter           &_json;
    bool                  _tokenize;

    virtual void visit(const AnnotationReferenceFieldValue & v ) {
        (void)v;
        _json.beginObject();
        _json.appendKey("error");
        _json.appendString("cannot convert from annotation reference field");
        _json.endObject();
    }
    virtual void visit(const Document & v) {
        (void)v;
        _json.beginObject();
        _json.appendKey("error");
        _json.appendString("cannot convert from field of type document");
        _json.endObject();
    }

    virtual void visit(const MapFieldValue & v) {
        _json.beginArray();
        for (const auto & entry : v) {
            _json.beginObject();

            _json.appendKey("key");
            const FieldValue &key = *(entry.first);
            key.accept(*this);

            const FieldValue &val = *(entry.second);
            _json.appendKey("value");
            val.accept(*this);

            _json.endObject();
        }
        _json.endArray();
    }

    virtual void visit(const ArrayFieldValue &value) {
        _json.beginArray();
        if (value.size() > 0) {
            for (const FieldValue &fv : value) {
                fv.accept(*this);
            }
        }
        _json.endArray();
    }

    virtual void visit(const StringFieldValue &value) {
        if (_tokenize) {
            asciistream tmp;
            SummaryHandler handler(value.getValue(), tmp);
            handleIndexingTerms(handler, value);
            _json.appendString(tmp.str());
        } else {
            _json.appendString(value.getValue());
        }
    }

    virtual void visit(const IntFieldValue &value) {
        int32_t v = value.getValue(); _json.appendInt64(v);
    }
    virtual void visit(const LongFieldValue &value) {
        int64_t v = value.getValue(); _json.appendInt64(v);
    }
    virtual void visit(const ShortFieldValue &value) {
        int16_t v = value.getValue(); _json.appendInt64(v);
    }
    virtual void visit(const ByteFieldValue &value) {
        int8_t v = value.getAsByte(); _json.appendInt64(v);
    }
    virtual void visit(const DoubleFieldValue &value) {
        double v = value.getValue(); _json.appendDouble(v);
    }
    virtual void visit(const FloatFieldValue &value) {
        float v = value.getValue(); _json.appendFloat(v);
    }

    virtual void
    visit(const PredicateFieldValue &value)
    {
        _json.appendJSON(value.toString());
    }

    virtual void
    visit(const RawFieldValue &value)
    {
        // Use base64 coding to represent raw values in json strings.
        std::pair<const char *, size_t> buf = value.getAsRaw();
        vespalib::string rawVal(buf.first, buf.first + buf.second);
        _json.appendString(vespalib::Base64::encode(rawVal));
    }

    virtual void visit(const StructFieldValue &value) {
        // stringref type_name = value.getDataType()->getName();
        if (*value.getDataType() == *SearchDataType::URI) {
            FieldValue::UP uriAllValue = value.getValue("all");
            if (uriAllValue.get() != NULL &&
                uriAllValue->inherits(IDENTIFIABLE_CLASSID(StringFieldValue)))
            {
                uriAllValue->accept(*this);
                return;
            }
        }
        _json.beginObject();
        for (StructFieldValue::const_iterator itr = value.begin(); itr != value.end(); ++itr) {
            _json.appendKey(itr.field().getName());
            FieldValue::UP nextValue(value.getValue(itr.field()));
            (*nextValue).accept(*this);
        }
        _json.endObject();
    }

    virtual void visit(const WeightedSetFieldValue &value) {
        _json.beginArray();
        if ( value.size() > 0) {
            for (const auto & entry : value) {
                _json.beginObject();
                _json.appendKey("item");
                entry.first->accept(*this);
                _json.appendKey("weight");
                int weight = static_cast<const IntFieldValue &>(*entry.second).getValue();
                _json.appendInt64(weight);
                _json.endObject();
            }
        }
        _json.endArray();
    }

    virtual void visit(const TensorFieldValue &value) override {
        const auto &tensor = value.getAsTensorPtr();
        if (tensor) {
            auto slime =
                vespalib::tensor::SlimeBinaryFormat::serialize(*tensor);
            vespalib::slime::SimpleBuffer buf;
            vespalib::slime::JsonFormat::encode(*slime, buf, true);
            _json.appendJSON(buf.get().make_string());
        } else {
            // No tensor value => empty object
            _json.beginObject();
            _json.endObject();
        }
    }

    void visit(const ReferenceFieldValue& value) override {
        _json.appendString(value.hasValidDocumentId()
                ? value.getDocumentId().toString()
                : string());
    }

public:
    JsonFiller(bool markup, JSONWriter &json)
        : _json(json), _tokenize(markup) {}
};

class JsonConverter : public FieldValueConverter {
    bool                  _tokenize;
public:
    JsonConverter(bool tokenize)
        : _tokenize(tokenize)
    {}

    FieldValue::UP convert(const FieldValue &input) {
        asciistream target;
        JSONWriter json(target);
        JsonFiller visitor(_tokenize, json);
        input.accept(visitor);
        return FieldValue::UP(new StringFieldValue(target.str()));
    }

};

class SummaryFieldValueConverter : protected ConstFieldValueVisitor
{
    asciistream          _str;
    bool                 _tokenize;
    FieldValue::UP       _field_value;
    FieldValueConverter &_structuredFieldConverter;

    virtual void visit(const ArrayFieldValue &value) {
        _field_value = _structuredFieldConverter.convert(value);
    }

    template <typename T>
    void visitPrimitive(const T &t) {
        _field_value.reset(t.clone());
    }
    virtual void visit(const IntFieldValue &value) { visitPrimitive(value); }
    virtual void visit(const LongFieldValue &value) { visitPrimitive(value); }
    virtual void visit(const ShortFieldValue &value) { visitPrimitive(value); }
    virtual void visit(const ByteFieldValue &value) {
        int8_t signedValue = value.getAsByte();
        _field_value.reset(new ShortFieldValue(signedValue));
    }
    virtual void visit(const DoubleFieldValue &value) { visitPrimitive(value); }
    virtual void visit(const FloatFieldValue &value) { visitPrimitive(value); }

    virtual void visit(const StringFieldValue &value) {
        if (_tokenize) {
            SummaryHandler handler(value.getValue(), _str);
            handleIndexingTerms(handler, value);
        } else {
            _str << value.getValue();
        }
    }

    virtual void visit(const AnnotationReferenceFieldValue & v ) {
        _field_value = _structuredFieldConverter.convert(v);
    }
    virtual void visit(const Document & v) {
        _field_value = _structuredFieldConverter.convert(v);
    }

    virtual void
    visit(const PredicateFieldValue &value)
    {
        _str << value.toString();
    }

    virtual void
    visit(const RawFieldValue &value)
    {
        visitPrimitive(value);
    }

    virtual void visit(const MapFieldValue & v) {
        _field_value = _structuredFieldConverter.convert(v);
    }

    virtual void visit(const StructFieldValue &value) {
        if (*value.getDataType() == *SearchDataType::URI) {
            FieldValue::UP uriAllValue = value.getValue("all");
            if (uriAllValue.get() != NULL &&
                uriAllValue->inherits(IDENTIFIABLE_CLASSID(StringFieldValue)))
            {
                uriAllValue->accept(*this);
                return;
            }
        }
        _field_value = _structuredFieldConverter.convert(value);
    }

    virtual void visit(const WeightedSetFieldValue &value) {
        _field_value = _structuredFieldConverter.convert(value);
    }

    virtual void visit(const TensorFieldValue &value) override {
        visitPrimitive(value);
    }

    void visit(const ReferenceFieldValue& value) override {
        if (value.hasValidDocumentId()) {
            _str << value.getDocumentId().toString();
        } // else:: implicit empty string
    }

public:
    SummaryFieldValueConverter(bool tokenize, FieldValueConverter &subConverter)
        : _str(), _tokenize(tokenize),
          _structuredFieldConverter(subConverter)
    {}

    FieldValue::UP convert(const FieldValue &input) {
        input.accept(*this);
        if (_field_value.get()) {
            return std::move(_field_value);
        }
        return FieldValue::UP(new StringFieldValue(_str.str()));
    }
};



using namespace vespalib::slime::convenience;

class SlimeFiller : public ConstFieldValueVisitor {
    Inserter    &_inserter;
    bool         _tokenize;

    virtual void visit(const AnnotationReferenceFieldValue & v ) {
        (void)v;
        Cursor &c = _inserter.insertObject();
        Memory key("error");
        Memory val("cannot convert from annotation reference field");
        c.setString(key, val);
    }
    virtual void visit(const Document & v) {
        (void)v;
        Cursor &c = _inserter.insertObject();
        Memory key("error");
        Memory val("cannot convert from field of type document");
        c.setString(key, val);
    }

    virtual void visit(const MapFieldValue & v) {
        Cursor &a = _inserter.insertArray();
        Memory keymem("key");
        Memory valmem("value");
        for (const auto & entry : v) {
            Cursor &c = a.addObject();
            ObjectInserter ki(c, keymem);
            ObjectInserter vi(c, valmem);
            SlimeFiller keyConv(ki, _tokenize);
            SlimeFiller valConv(vi, _tokenize);

            const FieldValue &key = *(entry.first);
            key.accept(keyConv);
            const FieldValue &val = *(entry.second);
            val.accept(valConv);
        }
    }

    virtual void visit(const ArrayFieldValue &value) {
        Cursor &a = _inserter.insertArray();
        if (value.size() > 0) {
            ArrayInserter ai(a);
            SlimeFiller conv(ai, _tokenize);
            for (const FieldValue &fv : value) {
                fv.accept(conv);
            }
        }
    }

    virtual void visit(const StringFieldValue &value) {
        if (_tokenize) {
            asciistream tmp;
            SummaryHandler handler(value.getValue(), tmp);
            handleIndexingTerms(handler, value);
            _inserter.insertString(Memory(tmp.str()));
        } else {
            _inserter.insertString(Memory(value.getValue()));
        }
    }

    virtual void visit(const IntFieldValue &value) {
        int32_t v = value.getValue();
        _inserter.insertLong(v);
    }
    virtual void visit(const LongFieldValue &value) {
        int64_t v = value.getValue();
        _inserter.insertLong(v);
    }
    virtual void visit(const ShortFieldValue &value) {
        int16_t v = value.getValue();
        _inserter.insertLong(v);
    }
    virtual void visit(const ByteFieldValue &value) {
        int8_t v = value.getAsByte();
        _inserter.insertLong(v);
    }
    virtual void visit(const DoubleFieldValue &value) {
        double v = value.getValue();
        _inserter.insertDouble(v);
    }
    virtual void visit(const FloatFieldValue &value) {
        float v = value.getValue();
        _inserter.insertDouble(v);
    }

    virtual void
    visit(const PredicateFieldValue &value)
    {
        vespalib::slime::inject(value.getSlime().get(), _inserter);
    }

    virtual void
    visit(const RawFieldValue &value)
    {
        // Use base64 coding to represent raw values
        std::pair<const char *, size_t> buf = value.getAsRaw();
        vespalib::string rawVal(buf.first, buf.first + buf.second);
        vespalib::string encVal(vespalib::Base64::encode(rawVal));
        _inserter.insertString(Memory(encVal.c_str()));
    }

    virtual void visit(const StructFieldValue &value) {
        if (*value.getDataType() == *SearchDataType::URI) {
            FieldValue::UP uriAllValue = value.getValue("all");
            if (uriAllValue.get() != NULL &&
                uriAllValue->inherits(IDENTIFIABLE_CLASSID(StringFieldValue)))
            {
                uriAllValue->accept(*this);
                return;
            }
        }
        Cursor &c = _inserter.insertObject();
        for (StructFieldValue::const_iterator itr = value.begin(); itr != value.end(); ++itr) {
            Memory keymem(itr.field().getName());
            ObjectInserter vi(c, keymem);
            SlimeFiller conv(vi, _tokenize);
            FieldValue::UP nextValue(value.getValue(itr.field()));
            (*nextValue).accept(conv);
        }
    }

    virtual void visit(const WeightedSetFieldValue &value) {
        Cursor &a = _inserter.insertArray();
        if (value.size() > 0) {
            Memory imem("item");
            Memory wmem("weight");
            for (const auto & entry : value) {
                Cursor &o = a.addObject();
                ObjectInserter ki(o, imem);
                SlimeFiller conv(ki, _tokenize);
                entry.first->accept(conv);
                int weight = static_cast<const IntFieldValue &>(*entry.second).getValue();
                o.setLong(wmem, weight);
            }
        }
    }

    virtual void visit(const TensorFieldValue &value) override {
        const auto &tensor = value.getAsTensorPtr();
        vespalib::nbostream s;
        if (tensor) {
            vespalib::tensor::TypedBinaryFormat::serialize(s, *tensor);
        }
        _inserter.insertData(vespalib::slime::Memory(s.peek(), s.size()));
    }

    void visit(const ReferenceFieldValue& value) override {
        _inserter.insertString(Memory(value.hasValidDocumentId()
                ? value.getDocumentId().toString()
                : string()));
    }

public:
    SlimeFiller(Inserter &inserter, bool tokenize)
        : _inserter(inserter), _tokenize(tokenize) {}
};

class SlimeConverter : public FieldValueConverter {
    bool _tokenize;
public:
    SlimeConverter(bool tokenize)
        : _tokenize(tokenize)
    {}

    FieldValue::UP convert(const FieldValue &input) {
        vespalib::Slime slime;
        SlimeInserter inserter(slime);
        SlimeFiller visitor(inserter, _tokenize);
        input.accept(visitor);
        search::RawBuf rbuf(4096);
        search::SlimeOutputRawBufAdapter adapter(rbuf);
        vespalib::slime::BinaryFormat::encode(slime, adapter);
        return FieldValue::UP(new RawFieldValue(rbuf.GetDrainPos(), rbuf.GetUsedLen()));
    }
};


}  // namespace

FieldValue::UP
SummaryFieldConverter::convertSummaryField(bool markup,
                                           const FieldValue &value,
                                           bool useSlimeInsideFields)
{
    if (useSlimeInsideFields) {
        SlimeConverter subConv(markup);
        return SummaryFieldValueConverter(markup, subConv).convert(value);
    } else {
        JsonConverter subConv(markup);
        return SummaryFieldValueConverter(markup, subConv).convert(value);
    }
}


}  // namespace proton
