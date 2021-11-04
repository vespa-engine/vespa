// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_inverter.h"
#include "document_inverter_context.h"
#include "i_field_index_collection.h"
#include "field_inverter.h"
#include "url_field_inverter.h"
#include <vespa/vespalib/util/isequencedtaskexecutor.h>

namespace search::memoryindex {

using document::Document;
using index::Schema;
using search::index::FieldLengthCalculator;

DocumentInverter::DocumentInverter(DocumentInverterContext& context)
    : _context(context),
      _inverters(),
      _urlInverters()
{
    auto& schema = context.get_schema();
    auto& field_indexes = context.get_field_indexes();
    for (uint32_t fieldId = 0; fieldId < schema.getNumIndexFields();
         ++fieldId) {
        auto &remover(field_indexes.get_remover(fieldId));
        auto &inserter(field_indexes.get_inserter(fieldId));
        auto &calculator(field_indexes.get_calculator(fieldId));
        _inverters.push_back(std::make_unique<FieldInverter>(schema, fieldId, remover, inserter, calculator));
    }
    auto& schema_index_fields = context.get_schema_index_fields();
    for (auto &urlField : schema_index_fields._uriFields) {
        Schema::CollectionType collectionType =
            schema.getIndexField(urlField._all).getCollectionType();
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
    _context.get_invert_threads().sync_all();
    _context.get_push_threads().sync_all();
}

void
DocumentInverter::invertDocument(uint32_t docId, const Document &doc)
{
    // Might want to batch inverters as we do for attributes
    _context.set_data_type(doc);
    auto& schema_index_fields = _context.get_schema_index_fields();
    auto& invert_threads = _context.get_invert_threads();
    for (uint32_t fieldId : schema_index_fields._textFields) {
        auto fv = _context.get_field_value(doc, fieldId);
        FieldInverter *inverter = _inverters[fieldId].get();
        invert_threads.execute(fieldId,[inverter, docId, fv(std::move(fv))]() {
            inverter->invertField(docId, fv);
        });
    }
    uint32_t urlId = 0;
    for (const auto & fi : schema_index_fields._uriFields) {
        uint32_t fieldId = fi._all;
        auto fv = _context.get_field_value(doc, fieldId);
        UrlFieldInverter *inverter = _urlInverters[urlId].get();
        invert_threads.execute(fieldId,[inverter, docId, fv(std::move(fv))]() {
            inverter->invertField(docId, fv);
        });
        ++urlId;
    }
}

void
DocumentInverter::removeDocument(uint32_t docId) {
    LidVector lids;
    lids.push_back(docId);
    removeDocuments(std::move(lids));
}
void
DocumentInverter::removeDocuments(LidVector lids)
{
    // Might want to batch inverters as we do for attributes
    auto& schema_index_fields = _context.get_schema_index_fields();
    auto& invert_threads = _context.get_invert_threads();
    for (uint32_t fieldId : schema_index_fields._textFields) {
        FieldInverter *inverter = _inverters[fieldId].get();
        invert_threads.execute(fieldId, [inverter, lids]() {
            for (uint32_t lid : lids) {
                inverter->removeDocument(lid);
            }
        });
    }
    uint32_t urlId = 0;
    for (const auto & fi : schema_index_fields._uriFields) {
        uint32_t fieldId = fi._all;
        UrlFieldInverter *inverter = _urlInverters[urlId].get();
        invert_threads.execute(fieldId, [inverter, lids]() {
            for (uint32_t lid : lids) {
                inverter->removeDocument(lid);
            }
        });
        ++urlId;
    }
}

void
DocumentInverter::pushDocuments(const std::shared_ptr<vespalib::IDestructorCallback> &onWriteDone)
{
    uint32_t fieldId = 0;
    auto& push_threads = _context.get_push_threads();
    for (auto &inverter : _inverters) {
        push_threads.execute(fieldId,[inverter(inverter.get()), onWriteDone]() {
            inverter->applyRemoves();
            inverter->pushDocuments();
        });
        ++fieldId;
    }
}

}

