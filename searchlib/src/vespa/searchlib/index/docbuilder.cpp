// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docbuilder.h"
#include <vespa/document/datatype/urldatatype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/text/utf8.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/data/slime/slime.h>

using namespace document;
using namespace search::index;

using search::index::schema::CollectionType;
using vespalib::Utf8Reader;
using vespalib::Utf8Writer;
using vespalib::geo::ZCurve;

namespace {

void
insertStr(const Schema::Field & sfield, document::FieldValue * fvalue, const vespalib::string & val)
{
    if (sfield.getDataType() == schema::DataType::STRING ||
        sfield.getDataType() == schema::DataType::RAW)
    {
        (dynamic_cast<LiteralFieldValueB *>(fvalue))->setValue(val);
    } else {
        throw DocBuilder::Error(vespalib::make_string("Field '%s' not compatible", sfield.getName().c_str()));
    }
}

void
insertInt(const Schema::Field & sfield, document::FieldValue * fvalue, int64_t val)
{
    if (sfield.getDataType() == schema::DataType::INT8) {
        (dynamic_cast<ByteFieldValue *>(fvalue))->setValue((uint8_t)val);
    } else if (sfield.getDataType() == schema::DataType::INT16) {
        (dynamic_cast<ShortFieldValue *>(fvalue))->setValue((int16_t)val);
    } else if (sfield.getDataType() == schema::DataType::INT32) {
        (dynamic_cast<IntFieldValue *>(fvalue))->setValue((int32_t)val);
    } else if (sfield.getDataType() == schema::DataType::INT64) {
        (dynamic_cast<LongFieldValue *>(fvalue))->setValue(val);
    } else {
        throw DocBuilder::Error(vespalib::make_string("Field '%s' not compatible", sfield.getName().c_str()));
    }
}

void
insertFloat(const Schema::Field & sfield, document::FieldValue * fvalue, double val)
{
    if (sfield.getDataType() == schema::DataType::FLOAT) {
        (dynamic_cast<FloatFieldValue *>(fvalue))->setValue((float)val);
    } else if (sfield.getDataType() == schema::DataType::DOUBLE) {
        (dynamic_cast<DoubleFieldValue *>(fvalue))->setValue(val);
    } else {
        throw DocBuilder::Error(vespalib::make_string("Field '%s' not compatible", sfield.getName().c_str()));
    }
}

void insertPredicate(const Schema::Field &sfield,
                     document::FieldValue *fvalue,
                     std::unique_ptr<vespalib::Slime> val) {
    if (sfield.getDataType() == schema::DataType::BOOLEANTREE) {
        *(dynamic_cast<PredicateFieldValue *>(fvalue)) =
            PredicateFieldValue(std::move(val));
    } else {
        throw DocBuilder::Error(vespalib::make_string(
                        "Field '%s' not compatible",
                        sfield.getName().c_str()));
    }
}

void insertTensor(const Schema::Field &schemaField,
                  document::FieldValue *fvalue,
                  std::unique_ptr<vespalib::tensor::Tensor> val) {
    if (schemaField.getDataType() == schema::DataType::TENSOR) {
        *(dynamic_cast<TensorFieldValue *>(fvalue)) = std::move(val);
    } else {
        throw DocBuilder::Error(vespalib::make_string(
                        "Field '%s' not compatible",
                        schemaField.getName().c_str()));
    }
}

void
insertPosition(const Schema::Field & sfield,
               document::FieldValue * fvalue, int32_t xpos, int32_t ypos)
{
    assert(*fvalue->getDataType() == *DataType::LONG);
    assert(sfield.getDataType() == schema::DataType::INT64);
    (void) sfield;
    int64_t zpos = ZCurve::encode(xpos, ypos);
    document::LongFieldValue *zvalue =
        dynamic_cast<LongFieldValue *>(fvalue);
    zvalue->setValue(zpos);
}


void
insertRaw(const Schema::Field & sfield,
          document::FieldValue *fvalue, const void *buf, size_t len)
{
    assert(*fvalue->getDataType() == *DataType::RAW);
    assert(sfield.getDataType() == schema::DataType::RAW);
    (void) sfield;
    document::RawFieldValue *rfvalue =
        dynamic_cast<RawFieldValue *>(fvalue);
    rfvalue->setValue(static_cast<const char *>(buf), len);
}


template <typename T>
std::unique_ptr<T>
make_UP(T *p)
{
    return std::unique_ptr<T>(p);
}

template <typename T>
std::unique_ptr<T>
makeUP(T *p)
{
    return std::unique_ptr<T>(p);
}

}  // namespace

namespace docbuilderkludge
{

namespace linguistics
{

const vespalib::string SPANTREE_NAME("linguistics");

enum TokenType {
    UNKNOWN = 0,
    SPACE = 1,
    PUNCTUATION = 2,
    SYMBOL = 3,
    ALPHABETIC = 4,
    NUMERIC = 5,
    MARKER = 6
};

}

}

using namespace docbuilderkludge;

namespace
{

Annotation::UP
makeTokenType(linguistics::TokenType type)
{
    return makeUP(new Annotation(*AnnotationType::TOKEN_TYPE,
                                 makeUP(new IntFieldValue(type))));
}

}

namespace search {
namespace index {

VESPA_IMPLEMENT_EXCEPTION(DocBuilderError, vespalib::Exception);

DocBuilder::FieldHandle::FieldHandle(const document::Field & dfield, const Schema::Field & field) :
    _sfield(field),
    _value(),
    _element()
{
    _value = dfield.createValue();
}


DocBuilder::CollectionFieldHandle::CollectionFieldHandle(const document::Field & dfield, const Schema::Field & field) :
    FieldHandle(dfield, field),
    _elementWeight(1)
{
}

void
DocBuilder::CollectionFieldHandle::startElement(int32_t weight)
{
    assert(_element.get() == NULL);
    _elementWeight = weight;
    const CollectionFieldValue * value = dynamic_cast<CollectionFieldValue *>(_value.get());
    _element = value->createNested();
}

void
DocBuilder::CollectionFieldHandle::endElement()
{
    if (_sfield.getCollectionType() == CollectionType::ARRAY) {
        onEndElement();
        ArrayFieldValue * value = dynamic_cast<ArrayFieldValue *>(_value.get());
        value->add(*_element);
    } else if (_sfield.getCollectionType() == CollectionType::WEIGHTEDSET) {
        onEndElement();
        WeightedSetFieldValue * value = dynamic_cast<WeightedSetFieldValue *>(_value.get());
        value->add(*_element, _elementWeight);
    } else {
        throw Error(vespalib::make_string("Field '%s' not compatible", _sfield.getName().c_str()));
    }
    _element.reset(NULL);
}


DocBuilder::IndexFieldHandle::IndexFieldHandle(const FixedTypeRepo & repo, const document::Field & dfield, const Schema::Field & sfield)
    : CollectionFieldHandle(dfield, sfield),
      _str(),
      _strSymbols(0u),
      _spanList(NULL),
      _spanTree(),
      _lastSpan(NULL),
      _spanStart(0u),
      _autoAnnotate(true),
      _autoSpace(true),
      _skipAutoSpace(true),
      _uriField(false),
      _subField(),
      _repo(repo)
{
    _str.reserve(1023);

    if (_sfield.getCollectionType() == CollectionType::SINGLE) {
        if (*_value->getDataType() == document::UrlDataType::getInstance())
            _uriField = true;
    } else {
        const CollectionFieldValue * value = dynamic_cast<CollectionFieldValue *>(_value.get());
        if (value->getNestedType() == document::UrlDataType::getInstance())
            _uriField = true;
    }
    startAnnotate();
}


void
DocBuilder::IndexFieldHandle::append(const vespalib::string &val)
{
    _strSymbols += val.size();
    _str += val;
}


void
DocBuilder::IndexFieldHandle::addStr(const vespalib::string &val)
{
    assert(_spanTree.get() != NULL);
    if (val.empty())
        return;
    if (!_skipAutoSpace && _autoSpace)
        addSpace();
    _skipAutoSpace = false;
    _spanStart = _strSymbols;
    append(val);
    if (_autoAnnotate) {
        addSpan();
        addTermAnnotation();
        if (val[0] >= '0' && val[0] <= '9') {
            addNumericTokenAnnotation();
        } else {
            addAlphabeticTokenAnnotation();
        }
    }
}


void
DocBuilder::IndexFieldHandle::addSpace()
{
    addNoWordStr(" ");
}


void
DocBuilder::IndexFieldHandle::addNoWordStr(const vespalib::string &val)
{
    assert(_spanTree.get() != NULL);
    if (val.empty())
        return;
    _spanStart = _strSymbols;
    append(val);
    if (_autoAnnotate) {
        addSpan();
        if (val[0] == ' ' || val[0] == '\t')
            addSpaceTokenAnnotation();
        else if (val[0] >= '0' && val[0] <= '9') {
            addNumericTokenAnnotation();
        } else {
            addAlphabeticTokenAnnotation();
        }

    }
    _skipAutoSpace = true;
}


void
DocBuilder::IndexFieldHandle::addTokenizedString(const vespalib::string &val,
        bool urlMode)
{
    Utf8Reader r(val);
    vespalib::string sbuf;
    Utf8Writer w(sbuf);
    uint32_t c = 0u;
    bool oldWord = false;
    assert(_uriField == urlMode);
    assert(_uriField != _subField.empty());

    while (r.hasMore()) {
        c = r.getChar();
        bool newWord = Fast_UnicodeUtil::IsWordChar(c) ||
                       (urlMode && (c == '-' || c == '_'));
        if (oldWord != newWord) {
            if (!sbuf.empty()) {
                if (oldWord)
                    addStr(sbuf);
                else
                    addNoWordStr(sbuf);
                sbuf.clear();
            }
            oldWord = newWord;
        }
        w.putChar(c);
    }
    if (!sbuf.empty()) {
        if (oldWord)
            addStr(sbuf);
        else
            addNoWordStr(sbuf);
    }
}


void
DocBuilder::IndexFieldHandle::addSpan(size_t start, size_t len)
{
    const SpanNode &span = _spanList->add(makeUP(new Span(start, len)));
    _lastSpan = &span;
}


void
DocBuilder::IndexFieldHandle::addSpan()
{
    size_t endPos = _strSymbols;
    assert(endPos > _spanStart);
    addSpan(_spanStart, endPos - _spanStart);
    _spanStart = endPos;
}


void
DocBuilder::IndexFieldHandle::addSpaceTokenAnnotation()
{
    assert(_spanTree.get() != NULL);
    assert(_lastSpan != NULL);
    _spanTree->annotate(*_lastSpan, makeTokenType(linguistics::SPACE));
}


void
DocBuilder::IndexFieldHandle::addNumericTokenAnnotation()
{
    assert(_spanTree.get() != NULL);
    assert(_lastSpan != NULL);
    _spanTree->annotate(*_lastSpan, makeTokenType(linguistics::NUMERIC));
}


void
DocBuilder::IndexFieldHandle::addAlphabeticTokenAnnotation()
{
    assert(_spanTree.get() != NULL);
    assert(_lastSpan != NULL);
    _spanTree->annotate(*_lastSpan, makeTokenType(linguistics::ALPHABETIC));
}


void
DocBuilder::IndexFieldHandle::addTermAnnotation()
{
    assert(_spanTree.get() != NULL);
    assert(_lastSpan != NULL);
    _spanTree->annotate(*_lastSpan, *AnnotationType::TERM);
}


void
DocBuilder::IndexFieldHandle::addTermAnnotation(const vespalib::string &val)
{
    assert(_spanTree.get() != NULL);
    assert(_lastSpan != NULL);
    _spanTree->annotate(*_lastSpan,
                        makeUP(new Annotation(*AnnotationType::TERM,
                                       makeUP(new StringFieldValue(val)))));
}


void
DocBuilder::IndexFieldHandle::onEndElement()
{
    // Flush data for index field.
    assert(_subField.empty());
    if (_uriField)
        return;
    StringFieldValue * value;
    if (_sfield.getCollectionType() != CollectionType::SINGLE) {
        value = dynamic_cast<StringFieldValue *>(_element.get());
    } else {
        value = dynamic_cast<StringFieldValue *>(_value.get());
    }
    value->setValue(_str);
    // Also drop all spans no annotation for now
    if (_spanTree->numAnnotations() > 0u) {
        StringFieldValue::SpanTrees trees;
        trees.emplace_back(std::move(_spanTree));
        value->setSpanTrees(trees, _repo);
    } else {
        _spanTree.reset();
    }
    _spanList = NULL;
    _lastSpan = NULL;
    _spanStart = 0u;
    _strSymbols = 0u;
    _str.clear();
    _skipAutoSpace = true;
    startAnnotate();
}


void
DocBuilder::IndexFieldHandle::onEndField()
{
    if (_sfield.getCollectionType() == CollectionType::SINGLE)
        onEndElement();
}


void
DocBuilder::IndexFieldHandle::startAnnotate()
{
    SpanList::UP span_list(new SpanList);
    _spanList = span_list.get();
    _spanTree.reset(new SpanTree(linguistics::SPANTREE_NAME, std::move(span_list)));
}


void
DocBuilder::IndexFieldHandle::setAutoAnnotate(bool autoAnnotate)
{
    _autoAnnotate = autoAnnotate;
}


void
DocBuilder::IndexFieldHandle::setAutoSpace(bool autoSpace)
{
    _autoSpace = autoSpace;
}


void
DocBuilder::IndexFieldHandle::startSubField(const vespalib::string &subField)
{
    assert(_subField.empty());
    assert(_uriField);
    _subField = subField;
}



void
DocBuilder::IndexFieldHandle::endSubField()
{
    assert(!_subField.empty());
    assert(_uriField);
    StructuredFieldValue *sValue;
    if (_sfield.getCollectionType() != CollectionType::SINGLE) {
        sValue = dynamic_cast<StructFieldValue *>(_element.get());
    } else {
        sValue = dynamic_cast<StructFieldValue *>(_value.get());
    }
    const Field &f = sValue->getField(_subField);
    FieldValue::UP fval(f.getDataType().createFieldValue());
    *fval = _str;
    StringFieldValue *value = dynamic_cast<StringFieldValue *>(fval.get());
    StringFieldValue::SpanTrees trees;
    trees.emplace_back(std::move(_spanTree));
    value->setSpanTrees(trees, _repo);
    sValue->setValue(f, *fval);
    _spanList = NULL;
    _lastSpan = NULL;
    _spanStart = 0u;
    _strSymbols = 0u;
    _str.clear();
    _skipAutoSpace = true;
    startAnnotate();
    _subField.clear();
}



DocBuilder::AttributeFieldHandle::
AttributeFieldHandle(const document::Field &dfield,
                     const Schema::Field &sfield)
    : CollectionFieldHandle(dfield, sfield)
{
}

void
DocBuilder::AttributeFieldHandle::addStr(const vespalib::string & val)
{
    if (_element.get() != NULL) {
        insertStr(_sfield, _element.get(), val);
    } else {
        insertStr(_sfield, _value.get(), val);
    }
}

void
DocBuilder::AttributeFieldHandle::addInt(int64_t val)
{
    if (_element.get() != NULL) {
        insertInt(_sfield, _element.get(), val);
    } else {
        insertInt(_sfield, _value.get(), val);
    }
}

void
DocBuilder::AttributeFieldHandle::addFloat(double val)
{
    if (_element.get() != NULL) {
        insertFloat(_sfield, _element.get(), val);
    } else {
        insertFloat(_sfield, _value.get(), val);
    }
}

void
DocBuilder::AttributeFieldHandle::addPredicate(
        std::unique_ptr<vespalib::Slime> val)
{
    if (_element.get() != NULL) {
        insertPredicate(_sfield, _element.get(), std::move(val));
    } else {
        insertPredicate(_sfield, _value.get(), std::move(val));
    }
}


void
DocBuilder::AttributeFieldHandle::addTensor(
        std::unique_ptr<vespalib::tensor::Tensor> val)
{
    if (_element.get() != NULL) {
        insertTensor(_sfield, _element.get(), std::move(val));
    } else {
        insertTensor(_sfield, _value.get(), std::move(val));
    }
}


void
DocBuilder::AttributeFieldHandle::addPosition(int32_t xpos, int32_t ypos)
{
    if (_element.get() != NULL) {
        insertPosition(_sfield, _element.get(), xpos, ypos);
    } else {
        insertPosition(_sfield, _value.get(), xpos, ypos);
    }
}


DocBuilder::SummaryFieldHandle::
SummaryFieldHandle(const document::Field & dfield,
                   const Schema::Field & sfield)
    : CollectionFieldHandle(dfield, sfield)
{
}

void
DocBuilder::SummaryFieldHandle::addStr(const vespalib::string & val)
{
    if (_element.get() != NULL) {
        insertStr(_sfield, _element.get(), val);
    } else {
        insertStr(_sfield, _value.get(), val);
    }
}

void
DocBuilder::SummaryFieldHandle::addInt(int64_t val)
{
    if (_element.get() != NULL) {
        insertInt(_sfield, _element.get(), val);
    } else {
        insertInt(_sfield, _value.get(), val);
    }
}

void
DocBuilder::SummaryFieldHandle::addFloat(double val)
{
    if (_element.get() != NULL) {
        insertFloat(_sfield, _element.get(), val);
    } else {
        insertFloat(_sfield, _value.get(), val);
    }
}


void
DocBuilder::SummaryFieldHandle::addRaw(const void *buf, size_t len)
{
    if (_element.get() != NULL) {
        insertRaw(_sfield, _element.get(), buf, len);
    } else {
        insertRaw(_sfield, _value.get(), buf, len);
    }
}


DocBuilder::DocumentHandle::DocumentHandle(document::Document &doc,
        const vespalib::string & docId)
    : _type(&doc.getType()),
      _doc(&doc),
      _fieldHandle(),
      _repo(*_doc->getRepo(), *_type)
{
    (void) docId;
}


DocBuilder::DocBuilder(const Schema &schema)
    : _schema(schema),
      _doctypes_config(DocTypeBuilder(schema).makeConfig()),
      _repo(new DocumentTypeRepo(_doctypes_config)),
      _docType(*_repo->getDocumentType("searchdocument")),
      _doc(),
      _handleDoc(),
      _currDoc()
{
}

DocBuilder::~DocBuilder() {}

DocBuilder &
DocBuilder::startDocument(const vespalib::string & docId)
{
    _doc.reset(new Document(_docType, DocumentId(docId)));
    _doc->setRepo(*_repo);
    _handleDoc.reset(new DocumentHandle(*_doc, docId));
    return *this;
}

document::Document::UP
DocBuilder::endDocument()
{
    _handleDoc->endDocument(_doc);
    return std::move(_doc);
}

DocBuilder &
DocBuilder::startIndexField(const vespalib::string & name)
{
    assert(_handleDoc->getFieldHandle().get() == NULL);
    uint32_t field_id = _schema.getIndexFieldId(name);
    assert(field_id != Schema::UNKNOWN_FIELD_ID);
    _handleDoc->startIndexField(_schema.getIndexField(field_id));
    _currDoc = _handleDoc.get();
    return *this;
}

DocBuilder &
DocBuilder::startAttributeField(const vespalib::string & name)
{
    assert(_handleDoc->getFieldHandle().get() == NULL);
    uint32_t field_id = _schema.getIndexFieldId(name);
    assert(field_id == Schema::UNKNOWN_FIELD_ID);
    field_id = _schema.getAttributeFieldId(name);
    assert(field_id != Schema::UNKNOWN_FIELD_ID);
    _handleDoc->startAttributeField(_schema.getAttributeField(field_id));
    _currDoc = _handleDoc.get();
    return *this;
}

DocBuilder &
DocBuilder::startSummaryField(const vespalib::string & name)
{
    assert(_handleDoc->getFieldHandle().get() == NULL);
    uint32_t field_id = _schema.getIndexFieldId(name);
    assert(field_id == Schema::UNKNOWN_FIELD_ID);
    field_id = _schema.getAttributeFieldId(name);
    assert(field_id == Schema::UNKNOWN_FIELD_ID);
    field_id = _schema.getSummaryFieldId(name);
    assert(field_id != Schema::UNKNOWN_FIELD_ID);
    _handleDoc->startSummaryField(_schema.getSummaryField(field_id));
    _currDoc = _handleDoc.get();
    return *this;
}

DocBuilder &
DocBuilder::endField()
{
    assert(_currDoc != NULL);
    _currDoc->endField();
    _currDoc = NULL;
    return *this;
}

DocBuilder &
DocBuilder::startElement(int32_t weight)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->startElement(weight);
    return *this;
}

DocBuilder &
DocBuilder::endElement()
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->endElement();
    return *this;
}

DocBuilder &
DocBuilder::addStr(const vespalib::string & str)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addStr(str);
    return *this;
}

DocBuilder &
DocBuilder::addSpace()
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addSpace();
    return *this;
}

DocBuilder &
DocBuilder::addNoWordStr(const vespalib::string & str)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addNoWordStr(str);
    return *this;
}

DocBuilder &
DocBuilder::addTokenizedString(const vespalib::string &str)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addTokenizedString(str, false);
    return *this;
}

DocBuilder &
DocBuilder::addUrlTokenizedString(const vespalib::string &str)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addTokenizedString(str, true);
    return *this;
}

DocBuilder &
DocBuilder::addInt(int64_t val)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addInt(val);
    return *this;
}

DocBuilder &
DocBuilder::addFloat(double val)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addFloat(val);
    return *this;
}


DocBuilder &
DocBuilder::addPredicate(std::unique_ptr<vespalib::Slime> val)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addPredicate(std::move(val));
    return *this;
}


DocBuilder &
DocBuilder::addTensor(std::unique_ptr<vespalib::tensor::Tensor> val)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addTensor(std::move(val));
    return *this;
}


DocBuilder &
DocBuilder::addSpan(size_t start, size_t len)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addSpan(start, len);
    return *this;
}


DocBuilder &
DocBuilder::addSpan()
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addSpan();
    return *this;
}


DocBuilder &
DocBuilder::addSpaceTokenAnnotation()
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addSpaceTokenAnnotation();
    return *this;
}


DocBuilder &
DocBuilder::addNumericTokenAnnotation()
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addNumericTokenAnnotation();
    return *this;
}


DocBuilder &
DocBuilder::addAlphabeticTokenAnnotation()
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addAlphabeticTokenAnnotation();
    return *this;
}


DocBuilder&
DocBuilder::addTermAnnotation()
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addTermAnnotation();
    return *this;
}


DocBuilder &
DocBuilder::addTermAnnotation(const vespalib::string &val)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addTermAnnotation(val);
    return *this;
}


DocBuilder &
DocBuilder::addPosition(int32_t xpos, int32_t ypos)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addPosition(xpos, ypos);
    return *this;
}


DocBuilder &
DocBuilder::addRaw(const void *buf, size_t len)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->addRaw(buf, len);
    return *this;
}


DocBuilder &
DocBuilder::startSubField(const vespalib::string &subField)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->startSubField(subField);
    return *this;
}


DocBuilder &
DocBuilder::endSubField()
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->endSubField();
    return *this;
}


DocBuilder &
DocBuilder::setAutoAnnotate(bool autoAnnotate)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->setAutoAnnotate(autoAnnotate);
    return *this;
}


DocBuilder &
DocBuilder::setAutoSpace(bool autoSpace)
{
    assert(_currDoc != NULL);
    _currDoc->getFieldHandle()->setAutoSpace(autoSpace);
    return *this;
}


} // namespace search::index
} // namespace search
