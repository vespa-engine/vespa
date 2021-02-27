// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_attribute_manager.h"
#include "i_attribute_writer.h"
#include <vespa/document/base/fieldpath.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace document { class DocumentType; }

namespace proton {

/**
 * Concrete attribute writer that handles writes in form of put, update and remove
 * to the attribute vectors managed by the underlying attribute manager.
 */
class AttributeWriter : public IAttributeWriter
{
private:
    using AttributeVector = search::AttributeVector;
    using FieldPath = document::FieldPath;
    using DataType = document::DataType;
    using DocumentType = document::DocumentType;
    using FieldValue = document::FieldValue;
    const IAttributeManager::SP _mgr;
    vespalib::ISequencedTaskExecutor &_attributeFieldWriter;
    vespalib::ThreadExecutor& _shared_executor;
    using ExecutorId = vespalib::ISequencedTaskExecutor::ExecutorId;
public:
    /**
     * Represents an attribute vector for a field and details about how to write to it.
     */
    class WriteField {
        FieldPath        _fieldPath;
        AttributeVector &_attribute;
        bool             _structFieldAttribute; // in array/map of struct
        bool             _use_two_phase_put;
    public:
        WriteField(AttributeVector &attribute);
        ~WriteField();
        AttributeVector &getAttribute() const { return _attribute; }
        const FieldPath &getFieldPath() const { return _fieldPath; }
        void buildFieldPath(const DocumentType &docType);
        bool isStructFieldAttribute() const { return _structFieldAttribute; }
        bool use_two_phase_put() const { return _use_two_phase_put; }
    };

    /**
     * Represents a set of fields (as attributes) that are handled by the same write thread.
     */
    class WriteContext {
        ExecutorId _executorId;
        std::vector<WriteField> _fields;
        bool _hasStructFieldAttribute;
        // When this is true, the context only contains a single field.
        bool _use_two_phase_put;
    public:
        WriteContext(ExecutorId executorId) noexcept;
        WriteContext(WriteContext &&rhs) noexcept;
        ~WriteContext();
        WriteContext &operator=(WriteContext &&rhs) noexcept;
        void buildFieldPaths(const DocumentType &docType);
        void add(AttributeVector &attr);
        ExecutorId getExecutorId() const { return _executorId; }
        const std::vector<WriteField> &getFields() const { return _fields; }
        bool hasStructFieldAttribute() const { return _hasStructFieldAttribute; }
        bool use_two_phase_put() const { return _use_two_phase_put; }
    };
private:
    using AttrWithId = std::pair<search::AttributeVector *, ExecutorId>;
    using AttrMap = vespalib::hash_map<vespalib::string, AttrWithId>;
    std::vector<WriteContext> _writeContexts;
    const DataType           *_dataType;
    bool                      _hasStructFieldAttribute;
    AttrMap                   _attrMap;

    void setupWriteContexts();
    void setupAttriuteMapping();
    void buildFieldPaths(const DocumentType &docType, const DataType *dataType);
    void internalPut(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                     bool allAttributes, OnWriteDoneType onWriteDone);
    void internalRemove(SerialNum serialNum, DocumentIdT lid, OnWriteDoneType onWriteDone);

public:
    AttributeWriter(proton::IAttributeManager::SP mgr);
    ~AttributeWriter() override;

    /* Only for in tests that add attributes after AttributeWriter construction. */

    /**
     * Implements IAttributeWriter.
     */
    std::vector<search::AttributeVector *> getWritableAttributes() const override;
    search::AttributeVector *getWritableAttribute(const vespalib::string &name) const override;
    void put(SerialNum serialNum, const Document &doc, DocumentIdT lid, OnWriteDoneType onWriteDone) override;
    void remove(SerialNum serialNum, DocumentIdT lid, OnWriteDoneType onWriteDone) override;
    void remove(const LidVector &lidVector, SerialNum serialNum, OnWriteDoneType onWriteDone) override;
    void update(SerialNum serialNum, const DocumentUpdate &upd, DocumentIdT lid,
                OnWriteDoneType onWriteDone, IFieldUpdateCallback & onUpdate) override;
    void update(SerialNum serialNum, const Document &doc, DocumentIdT lid, OnWriteDoneType onWriteDone) override;
    void heartBeat(SerialNum serialNum) override;
    void compactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum) override;
    const proton::IAttributeManager::SP &getAttributeManager() const override {
        return _mgr;
    }
    void forceCommit(const CommitParam & param, OnWriteDoneType onWriteDone) override;

    void onReplayDone(uint32_t docIdLimit) override;
    bool hasStructFieldAttribute() const override;

    // Should only be used for unit testing.
    const std::vector<WriteContext>& get_write_contexts() const {
        return _writeContexts;
    }
};

} // namespace proton

