// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_field_index_remove_listener.h"
#include <vespa/searchlib/index/schema_index_fields.h>

namespace document {
class DataType;
class Document;
class DocumentType;
class Field;
class FieldValue;
}

namespace search {
    class ISequencedTaskExecutor;
    class IDestructorCallback;
}

namespace search::memoryindex {

class FieldInverter;
class UrlFieldInverter;
class FieldIndexCollection;

class DocumentInverter {
private:
    DocumentInverter(const DocumentInverter &) = delete;
    DocumentInverter &operator=(const DocumentInverter &) = delete;

    const index::Schema &_schema;

    void addFieldPath(const document::DocumentType &docType, uint32_t fieldId);
    void buildFieldPath(const document::DocumentType & docType, const document::DataType *dataType);
    void invertNormalDocTextField(size_t fieldId, const document::FieldValue &field);
    void invertNormalDocUriField(const index::UriField &handle, const document::FieldValue &field);

    using FieldPath = document::Field;
    using IndexedFieldPaths = std::vector<std::unique_ptr<FieldPath>>;
    IndexedFieldPaths                   _indexedFieldPaths;
    const document::DataType *          _dataType;

    index::SchemaIndexFields  _schemaIndexFields;

    std::vector<std::unique_ptr<FieldInverter>> _inverters;
    std::vector<std::unique_ptr<UrlFieldInverter>> _urlInverters;
    ISequencedTaskExecutor &_invertThreads;
    ISequencedTaskExecutor &_pushThreads;

    /**
     * Obtain the schema used by this index.
     *
     * @return schema used by this index
     */
    const index::Schema &getSchema() const { return _schema; }

public:
    /**
     * Create a new memory index based on the given schema.
     *
     * @param schema the index schema to use
     */
    DocumentInverter(const index::Schema &schema,
                     ISequencedTaskExecutor &invertThreads,
                     ISequencedTaskExecutor &pushThreads);

    ~DocumentInverter();

    /**
     * Push inverted documents to memory field indexes.
     */
    void pushDocuments(FieldIndexCollection &fieldIndexes, const std::shared_ptr<IDestructorCallback> &onWriteDone);

    /**
     * Invert a document.
     *
     * @param docId            local id for document
     * @param doc              the document
     *
     **/
    void invertDocument(uint32_t docId, const document::Document &doc);

    /**
     * Remove a document.
     *
     * @param docId            local id for document
     */
    void removeDocument(uint32_t docId);

    FieldInverter *getInverter(uint32_t fieldId) const {
        return _inverters[fieldId].get();
    }

    const std::vector<std::unique_ptr<FieldInverter> > & getInverters() const { return _inverters; }

    uint32_t getNumFields() const { return _inverters.size(); }
};

}
