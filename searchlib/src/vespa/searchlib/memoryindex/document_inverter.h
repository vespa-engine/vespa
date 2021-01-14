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

namespace vespalib {
    class IDestructorCallback;
    class ISequencedTaskExecutor;
}

namespace search::memoryindex {

class FieldInverter;
class UrlFieldInverter;
class IFieldIndexCollection;

/**
 * Class used to invert the fields for a set of documents, preparing for pushing changes info field indexes.
 *
 * Each text and uri field in the document is handled separately by a FieldInverter and UrlFieldInverter.
 */
class DocumentInverter {
private:
    using ISequencedTaskExecutor = vespalib::ISequencedTaskExecutor;
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

    const index::Schema &getSchema() const { return _schema; }

public:
    /**
     * Create a new document inverter based on the given schema.
     *
     * @param schema        the schema with which text and uri fields to consider.
     * @param invertThreads the executor with threads for doing document inverting.
     * @param pushThreads   the executor with threads for doing pushing of inverted documents
     *                      to corresponding field indexes.
     */
    DocumentInverter(const index::Schema &schema,
                     ISequencedTaskExecutor &invertThreads,
                     ISequencedTaskExecutor &pushThreads,
                     IFieldIndexCollection &fieldIndexes);

    ~DocumentInverter();

    /**
     * Push the current batch of inverted documents to corresponding field indexes.
     *
     * This function is async:
     * For each field inverter a task for pushing the inverted documents to the corresponding field index
     * is added to the 'push threads' executor, then this function returns.
     * All tasks hold a reference to the 'onWriteDone' callback, so when the last task is completed,
     * the callback is destructed.
     *
     * NOTE: The caller of this function should sync the 'invert threads' executor first,
     * to ensure that inverting is completed before pushing starts.
     */
    void pushDocuments(const std::shared_ptr<vespalib::IDestructorCallback> &onWriteDone);

    /**
     * Invert (add) the given document.
     *
     * This function is async:
     * For each text and uri field in the document a task for inverting and adding that
     * field (using a field inverter) is added to the 'invert threads' executor, then this function returns.
     **/
    void invertDocument(uint32_t docId, const document::Document &doc);

    /**
     * Remove the given document.
     *
     * This function is async:
     * For each text and uri field in the index schema a task for removing this document
     * (using a field inverter) is added to the 'invert threads' executor', then this function returns.
     */
    void removeDocument(uint32_t docId);

    FieldInverter *getInverter(uint32_t fieldId) const {
        return _inverters[fieldId].get();
    }

    const std::vector<std::unique_ptr<FieldInverter> > & getInverters() const { return _inverters; }

    uint32_t getNumFields() const { return _inverters.size(); }
};

}
