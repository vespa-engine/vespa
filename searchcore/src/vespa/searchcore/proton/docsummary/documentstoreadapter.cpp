// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentstoreadapter.h"
#include <vespa/searchsummary/docsummary/summaryfieldconverter.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/serialization/typed_binary_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.documentstoreadapter");

using namespace document;
using namespace search::docsummary;
using vespalib::tensor::Tensor;

namespace proton {

namespace {

const vespalib::string DOCUMENT_ID_FIELD("documentid");

}

bool
DocumentStoreAdapter::writeStringField(const char * buf,
                                       uint32_t buflen,
                                       ResType type)
{
    switch (type) {
    case RES_STRING:
        return _resultPacker.AddString(buf, buflen);
    case RES_LONG_STRING:
    case RES_XMLSTRING:
    case RES_JSONSTRING:
        return _resultPacker.AddLongString(buf, buflen);
    default:
        break;
    }
    return false;
}

bool
DocumentStoreAdapter::writeField(const FieldValue &value, ResType type)
{
    switch (type) {
    case RES_BYTE:
        return _resultPacker.AddByte(value.getAsInt());
    case RES_SHORT:
        return _resultPacker.AddShort(value.getAsInt());
    case RES_INT:
        return _resultPacker.AddInteger(value.getAsInt());
    case RES_INT64:
        return _resultPacker.AddInt64(value.getAsLong());
    case RES_FLOAT:
        return _resultPacker.AddFloat(value.getAsFloat());
    case RES_DOUBLE:
        return _resultPacker.AddDouble(value.getAsDouble());
    case RES_STRING:
    case RES_LONG_STRING:
    case RES_XMLSTRING:
    case RES_JSONSTRING:
        {
            if (value.getClass().inherits(LiteralFieldValueB::classId)) {
                const LiteralFieldValueB & lfv =
                    static_cast<const LiteralFieldValueB &>(value);
                vespalib::stringref s = lfv.getValueRef();
                return writeStringField(s.c_str(), s.size(), type);
            } else {
                vespalib::string s = value.getAsString();
                return writeStringField(s.c_str(), s.size(), type);
            }
        }
    case RES_DATA:
        {
            std::pair<const char *, size_t> buf = value.getAsRaw();
            return _resultPacker.AddData(buf.first, buf.second);
        }
    case RES_LONG_DATA:
        {
            std::pair<const char *, size_t> buf = value.getAsRaw();
            return _resultPacker.AddLongData(buf.first, buf.second);
        }
    case RES_TENSOR:
        {
            vespalib::nbostream serialized;
            if (value.getClass().inherits(TensorFieldValue::classId)) {
                const TensorFieldValue &tvalue = static_cast<const TensorFieldValue &>(value);
                const std::unique_ptr<Tensor> &tensor = tvalue.getAsTensorPtr();
                if (tensor) {
                    vespalib::tensor::TypedBinaryFormat::serialize(serialized, *tensor);
                }
            }
            return _resultPacker.AddSerializedTensor(serialized.peek(), serialized.size());
        }
    default:
        LOG(warning,
            "Unknown docsum field type: %s. Add empty field",
            ResultConfig::GetResTypeName(type));
        return _resultPacker.AddEmpty();
    }
    return false;
}


void
DocumentStoreAdapter::convertFromSearchDoc(Document &doc, uint32_t docId)
{
    for (size_t i = 0; i < _resultClass->GetNumEntries(); ++i) {
        const ResConfigEntry * entry = _resultClass->GetEntry(i);
        const vespalib::string fieldName(entry->_bindname);
        bool markup = _markupFields.find(fieldName) != _markupFields.end();
        if (fieldName == DOCUMENT_ID_FIELD) {
            StringFieldValue value(doc.getId().toString());
            if (!writeField(value, entry->_type)) {
                LOG(warning, "Error while writing field '%s' for docId %u",
                    fieldName.c_str(), docId);
            }
            continue;
        }
        const Field *field = _fieldCache->getField(i);
        if (!field) {
            LOG(debug,
                "Did not find field '%s' in the document "
                "for docId %u. Adding empty field",
                fieldName.c_str(), docId);
            _resultPacker.AddEmpty();
            continue;
        }
        FieldValue::UP fieldValue = doc.getValue(*field);
        if (fieldValue.get() == NULL) {
            LOG(spam,
                "No field value for field '%s' in the document "
                "for docId %u. Adding empty field",
                fieldName.c_str(), docId);
            _resultPacker.AddEmpty();
            continue;
        }
        LOG(spam,
            "writeField(%s): value(%s), type(%d)",
            fieldName.c_str(), fieldValue->toString().c_str(),
            entry->_type);
        FieldValue::UP convertedFieldValue =
            SummaryFieldConverter::convertSummaryField(markup, *fieldValue);
        if (convertedFieldValue.get() != NULL) {
            if (!writeField(*convertedFieldValue, entry->_type)) {
                LOG(warning,
                    "Error while writing field '%s' for docId %u",
                    fieldName.c_str(), docId);
            }
        } else {
            LOG(spam,
                "No converted field value for field '%s' "
                " in the document "
                "for docId %u. Adding empty field",
                fieldName.c_str(), docId);
            _resultPacker.AddEmpty();
        }
    }
}

DocumentStoreAdapter::
DocumentStoreAdapter(const search::IDocumentStore & docStore,
                     const DocumentTypeRepo &repo,
                     const ResultConfig & resultConfig,
                     const vespalib::string & resultClassName,
                     const FieldCache::CSP & fieldCache,
                     const std::set<vespalib::string> &markupFields)
    : _docStore(docStore),
      _repo(repo),
      _resultConfig(resultConfig),
      _resultClass(resultConfig.
                   LookupResultClass(resultConfig.
                                     LookupResultClassId(resultClassName.
                                             c_str()))),
      _resultPacker(&_resultConfig),
      _fieldCache(fieldCache),
      _markupFields(markupFields)
{
}

DocumentStoreAdapter::~DocumentStoreAdapter() {}

DocsumStoreValue
DocumentStoreAdapter::getMappedDocsum(uint32_t docId)
{
    if (!_resultPacker.Init(getSummaryClassId())) {
        LOG(warning,
            "Error during init of result class '%s' with class id %u",
            _resultClass->GetClassName(), getSummaryClassId());
        return DocsumStoreValue();
    }
    Document::UP document = _docStore.read(docId, _repo);
    if (document.get() == NULL) {
        LOG(debug,
            "Did not find summary document for docId %u. "
            "Returning empty docsum",
            docId);
        return DocsumStoreValue();
    }
    LOG(spam,
        "getMappedDocSum(%u): document={\n%s\n}",
        docId,
        document->toString(true).c_str());
    convertFromSearchDoc(*document, docId);
    const char * buf;
    uint32_t buflen;
    if (!_resultPacker.GetDocsumBlob(&buf, &buflen)) {
        LOG(warning,
            "Error while getting the docsum blob for docId %u. "
            "Returning empty docsum",
            docId);
        return DocsumStoreValue();
    }
    return DocsumStoreValue(buf, buflen);
}

} // namespace proton
