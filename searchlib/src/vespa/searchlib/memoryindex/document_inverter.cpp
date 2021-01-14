// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_inverter.h"
#include "i_field_index_collection.h"
#include "field_inverter.h"
#include "ordered_field_index_inserter.h"
#include "url_field_inverter.h"
#include <vespa/document/annotation/alternatespanlist.h>
#include <vespa/document/datatype/urldatatype.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/searchlib/common/sort.h>
#include <vespa/searchlib/util/url.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/text/utf8.h>
#include <stdexcept>

#include <vespa/log/log.h>
LOG_SETUP(".memoryindex.document_inverter");

namespace search::memoryindex {

using document::Field;
using document::FieldValue;
using document::Document;
using document::ArrayFieldValue;
using document::WeightedSetFieldValue;
using document::StringFieldValue;
using document::IntFieldValue;
using document::StructFieldValue;
using document::DataType;
using document::DocumentType;
using document::AlternateSpanList;
using document::Span;
using document::SpanList;
using document::SimpleSpanList;
using document::SpanNode;
using index::DocIdAndPosOccFeatures;
using index::Schema;
using search::util::URL;
using search::index::FieldLengthCalculator;

DocumentInverter::DocumentInverter(const Schema &schema,
                                   ISequencedTaskExecutor &invertThreads,
                                   ISequencedTaskExecutor &pushThreads,
                                   IFieldIndexCollection &fieldIndexes)
    : _schema(schema),
      _indexedFieldPaths(),
      _dataType(nullptr),
      _schemaIndexFields(),
      _inverters(),
      _urlInverters(),
      _invertThreads(invertThreads),
      _pushThreads(pushThreads)
{
    _schemaIndexFields.setup(schema);

    for (uint32_t fieldId = 0; fieldId < _schema.getNumIndexFields();
         ++fieldId) {
        auto &remover(fieldIndexes.get_remover(fieldId));
        auto &inserter(fieldIndexes.get_inserter(fieldId));
        auto &calculator(fieldIndexes.get_calculator(fieldId));
        _inverters.push_back(std::make_unique<FieldInverter>(_schema, fieldId, remover, inserter, calculator));
    }
    for (auto &urlField : _schemaIndexFields._uriFields) {
        Schema::CollectionType collectionType =
            _schema.getIndexField(urlField._all).getCollectionType();
        _urlInverters.push_back(std::make_unique<UrlFieldInverter>
                                (collectionType,
                                 _inverters[urlField._all].get(),
                                 _inverters[urlField._scheme].get(),
                                 _inverters[urlField._host].get(),
                                 _inverters[urlField._port].get(),
                                 _inverters[urlField._path].get(),
                                 _inverters[urlField._query].get(),
                                 _inverters[urlField._fragment].get(),
                                 _inverters[urlField._hostname].get()));
    }
}

DocumentInverter::~DocumentInverter()
{
    _invertThreads.sync();
    _pushThreads.sync();
}

void
DocumentInverter::addFieldPath(const document::DocumentType &docType,
                               uint32_t fieldId)
{
    assert(fieldId < _indexedFieldPaths.size());
    std::unique_ptr<FieldPath> fp;
    if ( ! docType.hasField(_schema.getIndexField(fieldId).getName())) {
        LOG(error,
            "Mismatch between documentdefinition and schema. "
            "No field named '%s' from schema in document type '%s'",
            _schema.getIndexField(fieldId).getName().c_str(),
            docType.getName().c_str());
    } else {
        fp = std::make_unique<Field>(docType.getField(_schema.getIndexField(fieldId).getName()));
    }
    _indexedFieldPaths[fieldId] = std::move(fp);
}

void
DocumentInverter::buildFieldPath(const document::DocumentType &docType,
                                 const document::DataType *dataType)
{
    _indexedFieldPaths.clear();
    _indexedFieldPaths.resize(_schema.getNumIndexFields());
    for (const auto & fi : _schemaIndexFields._textFields) {
        addFieldPath(docType, fi);
    }
    for (const auto & fi : _schemaIndexFields._uriFields) {
        addFieldPath(docType, fi._all);
    }
    _dataType = dataType;
}

void
DocumentInverter::invertDocument(uint32_t docId, const Document &doc)
{
    const document::DataType *dataType(doc.getDataType());
    if (_indexedFieldPaths.empty() || _dataType != dataType) {
        buildFieldPath(doc.getType(), dataType);
    }
    for (uint32_t fieldId : _schemaIndexFields._textFields) {
        const FieldPath *const fieldPath(_indexedFieldPaths[fieldId].get());
        FieldValue::UP fv;
        if (fieldPath != nullptr) {
            // TODO: better handling of input data (and better input data)
            // FieldValue::UP fv = doc.getNestedFieldValue(fieldPath.begin(), fieldPath.end());
            fv = doc.getValue(*fieldPath);
        }
        FieldInverter *inverter = _inverters[fieldId].get();
        _invertThreads.execute(fieldId,
                               [inverter, docId, fv(std::move(fv))]()
                               { inverter->invertField(docId, fv); });
    }
    uint32_t urlId = 0;
    for (const auto & fi : _schemaIndexFields._uriFields) {
        uint32_t fieldId = fi._all;
        const FieldPath *const fieldPath(_indexedFieldPaths[fieldId].get());
        FieldValue::UP fv;
        if (fieldPath != nullptr) {
            // TODO: better handling of input data (and better input data)
            // FieldValue::UP fv = doc.getNestedFieldValue(fieldPath.begin(), fieldPath.end());
            fv = doc.getValue(*fieldPath);
        }
        UrlFieldInverter *inverter = _urlInverters[urlId].get();
        _invertThreads.execute(fieldId,
                               [inverter, docId, fv(std::move(fv))]()
                               { inverter->invertField(docId, fv); });
        ++urlId;
    }
}

void
DocumentInverter::removeDocument(uint32_t docId)
{
    for (uint32_t fieldId : _schemaIndexFields._textFields) {
        FieldInverter *inverter = _inverters[fieldId].get();
        _invertThreads.execute(fieldId,
                               [inverter, docId]()
                               { inverter->removeDocument(docId); });
    }
    uint32_t urlId = 0;
    for (const auto & fi : _schemaIndexFields._uriFields) {
        uint32_t fieldId = fi._all;
        UrlFieldInverter *inverter = _urlInverters[urlId].get();
        _invertThreads.execute(fieldId,
                               [inverter, docId]()
                               { inverter->removeDocument(docId); });
        ++urlId;
    }
}

void
DocumentInverter::pushDocuments(const std::shared_ptr<vespalib::IDestructorCallback> &onWriteDone)
{
    uint32_t fieldId = 0;
    for (auto &inverter : _inverters) {
        _pushThreads.execute(fieldId,
                             [inverter(inverter.get()),
                              onWriteDone]()
                             {   inverter->applyRemoves();
                                 inverter->pushDocuments(); });
        ++fieldId;
    }
}

}

