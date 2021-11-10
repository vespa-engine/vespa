// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/monitored_refcount.h>

#include <cstdint>
#include <memory>
#include <vector>

namespace document {
    class DataType;
    class Document;
    class DocumentType;
    class Field;
    class FieldValue;
}

namespace vespalib { class IDestructorCallback; }

namespace search::memoryindex {

class DocumentInverterContext;
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
    DocumentInverter(const DocumentInverter &) = delete;
    DocumentInverter &operator=(const DocumentInverter &) = delete;

    DocumentInverterContext& _context;

    using LidVector = std::vector<uint32_t>;
    using OnWriteDoneType = const std::shared_ptr<vespalib::IDestructorCallback> &;

    std::vector<std::unique_ptr<FieldInverter>> _inverters;
    std::vector<std::unique_ptr<UrlFieldInverter>> _urlInverters;
    vespalib::MonitoredRefCount                    _ref_count;

public:
    /**
     * Create a new document inverter based on the given schema.
     *
     * @param context       A document inverter context shared between related document inverters.
     */
    DocumentInverter(DocumentInverterContext& context);

    ~DocumentInverter();

    /**
     * Push the current batch of inverted documents to corresponding field indexes.
     *
     * This function is async:
     * For each field inverter a task for pushing the inverted documents to the corresponding field index
     * is added to the 'push threads' executor, then this function returns.
     * All tasks hold a reference to the 'on_write_done' callback, so when the last task is completed,
     * the callback is destructed.
     *
     * NOTE: The caller of this function should sync the 'invert threads' executor first,
     * to ensure that inverting is completed before pushing starts.
     */
    void pushDocuments(OnWriteDoneType on_write_done);

    /**
     * Invert (add) the given document.
     *
     * This function is async:
     * For each text and uri field in the document a task for inverting and adding that
     * field (using a field inverter) is added to the 'invert threads' executor, then this function returns.
     **/
    void invertDocument(uint32_t docId, const document::Document &doc, OnWriteDoneType on_write_done);

    /**
     * Remove the given document.
     *
     * This function is async:
     * For each text and uri field in the index schema a task for removing this document
     * (using a field inverter) is added to the 'invert threads' executor', then this function returns.
     */
    void removeDocument(uint32_t docId);
    void removeDocuments(LidVector lids);

    FieldInverter *getInverter(uint32_t fieldId) const {
        return _inverters[fieldId].get();
    }

    uint32_t getNumFields() const { return _inverters.size(); }
    void wait_for_zero_ref_count() { _ref_count.waitForZeroRefCount(); }
    bool has_zero_ref_count() { return _ref_count.has_zero_ref_count(); }
    vespalib::MonitoredRefCount& get_ref_count() noexcept { return _ref_count; }
};

}
