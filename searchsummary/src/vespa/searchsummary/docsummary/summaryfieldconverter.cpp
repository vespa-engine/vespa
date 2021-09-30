// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryfieldconverter.h"
#include "linguisticsannotation.h"
#include "searchdatatype.h"
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/annotation/spantreevisitor.h>
#include <vespa/document/datatype/documenttype.h>
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
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/util/url.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/searchlib/util/slime_output_raw_buf_adapter.h>
#include <vespa/vespalib/util/exceptions.h>


using document::AlternateSpanList;
using document::Annotation;
using document::AnnotationReferenceFieldValue;
using document::ArrayDataType;
using document::ArrayFieldValue;
using document::BoolFieldValue;
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
using vespalib::asciistream;
using vespalib::geo::ZCurve;
using vespalib::make_string;
using vespalib::string;
using vespalib::stringref;

namespace search::docsummary {

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


class SummaryFieldValueConverter : protected ConstFieldValueVisitor
{
    asciistream          _str;
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
        _field_value.reset(new ShortFieldValue(signedValue));
    }
    void visit(const DoubleFieldValue &value) override { visitPrimitive(value); }
    void visit(const FloatFieldValue &value) override { visitPrimitive(value); }

    void visit(const StringFieldValue &value) override {
        if (_tokenize) {
            SummaryHandler handler(value.getValue(), _str);
            handleIndexingTerms(handler, value);
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
            if (uriAllValue &&
                uriAllValue->inherits(IDENTIFIABLE_CLASSID(StringFieldValue)))
            {
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
    ~SummaryFieldValueConverter();

    FieldValue::UP convert(const FieldValue &input) {
        input.accept(*this);
        if (_field_value.get()) {
            return std::move(_field_value);
        }
        return std::make_unique<StringFieldValue>(_str.str());
    }
};

SummaryFieldValueConverter::SummaryFieldValueConverter(bool tokenize, FieldValueConverter &subConverter)
    : _str(), _tokenize(tokenize),
      _structuredFieldConverter(subConverter)
{}
SummaryFieldValueConverter::~SummaryFieldValueConverter() = default;

using namespace vespalib::slime::convenience;



class SlimeFiller : public ConstFieldValueVisitor {
private:
    class MapFieldValueInserter {
    private:
        Cursor& _array;
        Symbol _key_sym;
        Symbol _val_sym;
        bool _tokenize;

    public:
        MapFieldValueInserter(Inserter& parent_inserter, bool tokenize)
            : _array(parent_inserter.insertArray()),
              _key_sym(_array.resolve("key")),
              _val_sym(_array.resolve("value")),
              _tokenize(tokenize)
        {
        }
        void insert_entry(const FieldValue& key, const FieldValue& value) {
            Cursor& c = _array.addObject();
            ObjectSymbolInserter ki(c, _key_sym);
            ObjectSymbolInserter vi(c, _val_sym);
            SlimeFiller key_conv(ki, _tokenize);
            SlimeFiller val_conv(vi, _tokenize);

            key.accept(key_conv);
            value.accept(val_conv);
        }
    };

    Inserter    &_inserter;
    bool         _tokenize;
    const std::vector<uint32_t>* _matching_elems;

    bool filter_matching_elements() const {
        return _matching_elems != nullptr;
    }

    void visit(const AnnotationReferenceFieldValue & v ) override {
        (void)v;
        Cursor &c = _inserter.insertObject();
        Memory key("error");
        Memory val("cannot convert from annotation reference field");
        c.setString(key, val);
    }
    void visit(const Document & v) override {
        (void)v;
        Cursor &c = _inserter.insertObject();
        Memory key("error");
        Memory val("cannot convert from field of type document");
        c.setString(key, val);
    }

    void visit(const MapFieldValue & v) override {
        MapFieldValueInserter map_inserter(_inserter, _tokenize);
        if (filter_matching_elements()) {
            assert(v.has_no_erased_keys());
            if (!_matching_elems->empty() && _matching_elems->back() < v.size()) {
                for (uint32_t id_to_keep : (*_matching_elems)) {
                    auto entry = v[id_to_keep];
                    map_inserter.insert_entry(*entry.first, *entry.second);
                }
            }
        } else {
            for (const auto &entry : v) {
                map_inserter.insert_entry(*entry.first, *entry.second);
            }
        }
    }

    void visit(const ArrayFieldValue &value) override {
        Cursor &a = _inserter.insertArray();
        if (value.size() > 0) {
            ArrayInserter ai(a);
            SlimeFiller conv(ai, _tokenize);
            if (filter_matching_elements()) {
                if (!_matching_elems->empty() && _matching_elems->back() < value.size()) {
                    for (uint32_t id_to_keep : (*_matching_elems)) {
                        value[id_to_keep].accept(conv);
                    }
                }
            } else {
                for (const FieldValue &fv : value) {
                    fv.accept(conv);
                }
            }
        }
    }

    void visit(const StringFieldValue &value) override {
        if (_tokenize) {
            asciistream tmp;
            SummaryHandler handler(value.getValue(), tmp);
            handleIndexingTerms(handler, value);
            _inserter.insertString(Memory(tmp.str()));
        } else {
            _inserter.insertString(Memory(value.getValue()));
        }
    }

    void visit(const IntFieldValue &value) override {
        int32_t v = value.getValue();
        _inserter.insertLong(v);
    }
    void visit(const LongFieldValue &value) override {
        int64_t v = value.getValue();
        _inserter.insertLong(v);
    }
    void visit(const ShortFieldValue &value) override {
        int16_t v = value.getValue();
        _inserter.insertLong(v);
    }
    void visit(const ByteFieldValue &value) override {
        int8_t v = value.getAsByte();
        _inserter.insertLong(v);
    }
    void visit(const BoolFieldValue &value) override {
        bool v = value.getValue();
        _inserter.insertBool(v);
    }
    void visit(const DoubleFieldValue &value) override {
        double v = value.getValue();
        _inserter.insertDouble(v);
    }
    void visit(const FloatFieldValue &value) override {
        float v = value.getValue();
        _inserter.insertDouble(v);
    }

    void visit(const PredicateFieldValue &value) override {
        vespalib::slime::inject(value.getSlime().get(), _inserter);
    }

    void visit(const RawFieldValue &value) override {
        std::pair<const char *, size_t> buf = value.getAsRaw();
        _inserter.insertData(Memory(buf.first, buf.second));
    }

    void visit(const StructFieldValue &value) override {
        if (*value.getDataType() == *SearchDataType::URI) {
            FieldValue::UP uriAllValue = value.getValue("all");
            if (uriAllValue &&
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

    void visit(const WeightedSetFieldValue &value) override {
        Cursor &a = _inserter.insertArray();
        if (value.size() > 0) {
            Symbol isym = a.resolve("item");
            Symbol wsym = a.resolve("weight");
            for (const auto & entry : value) {
                Cursor &o = a.addObject();
                ObjectSymbolInserter ki(o, isym);
                SlimeFiller conv(ki, _tokenize);
                entry.first->accept(conv);
                int weight = static_cast<const IntFieldValue &>(*entry.second).getValue();
                o.setLong(wsym, weight);
            }
        }
    }

    void visit(const TensorFieldValue &value) override {
        const auto &tensor = value.getAsTensorPtr();
        vespalib::nbostream s;
        if (tensor) {
            encode_value(*tensor, s);
        }
        _inserter.insertData(vespalib::Memory(s.peek(), s.size()));
    }

    void visit(const ReferenceFieldValue& value) override {
        _inserter.insertString(Memory(value.hasValidDocumentId()
                ? value.getDocumentId().toString()
                : string()));
    }

public:
    SlimeFiller(Inserter &inserter, bool tokenize)
        : _inserter(inserter),
          _tokenize(tokenize),
          _matching_elems()
    {}

    SlimeFiller(Inserter& inserter, bool tokenize, const std::vector<uint32_t>* matching_elems)
            : _inserter(inserter),
              _tokenize(tokenize),
              _matching_elems(matching_elems)
    {}
};

class SlimeConverter : public FieldValueConverter {
private:
    bool _tokenize;
    const std::vector<uint32_t>* _matching_elems;

public:
    SlimeConverter(bool tokenize)
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
        search::RawBuf rbuf(4_Ki);
        search::SlimeOutputRawBufAdapter adapter(rbuf);
        vespalib::slime::BinaryFormat::encode(slime, adapter);
        return std::make_unique<RawFieldValue>(rbuf.GetDrainPos(), rbuf.GetUsedLen());
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

FieldValue::UP
SummaryFieldConverter::convert_field_with_filter(bool markup,
                                                 const document::FieldValue& value,
                                                 const std::vector<uint32_t>& matching_elems)
{
    SlimeConverter sub_conv(markup, matching_elems);
    return SummaryFieldValueConverter(markup, sub_conv).convert(value);
}


}
