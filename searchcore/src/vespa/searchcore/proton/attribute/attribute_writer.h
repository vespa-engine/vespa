// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_attribute_manager.h"
#include "i_attribute_writer.h"
#include <vespa/searchcore/proton/common/commit_time_tracker.h>

namespace proton {

/**
 * Concrete attribute writer that handles writes in form of put, update and remove
 * to the attribute vectors managed by the underlying attribute manager.
 */
class AttributeWriter : public IAttributeWriter
{
private:
    typedef search::AttributeVector AttributeVector;
    typedef document::FieldPath FieldPath;
    typedef document::DataType DataType;
    typedef document::DocumentType DocumentType;
    typedef document::FieldValue FieldValue;
    const IAttributeManager::SP _mgr;
    search::ISequencedTaskExecutor &_attributeFieldWriter;
    const std::vector<search::AttributeVector *> &_writableAttributes;
public:
    class WriteField
    {
        FieldPath        _fieldPath;
        AttributeVector &_attribute;
        bool             _structFieldAttribute; // in array/map of struct
    public:
        WriteField(AttributeVector &attribute);
        ~WriteField();
        AttributeVector &getAttribute() const { return _attribute; }
        const FieldPath &getFieldPath() const { return _fieldPath; }
        void buildFieldPath(const DocumentType &docType);
        bool isStructFieldAttribute() const { return _structFieldAttribute; }
    };
    class WriteContext
    {
        uint32_t _executorId;
        std::vector<WriteField> _fields;
        bool _hasStructFieldAttribute;
    public:
        WriteContext(uint32_t executorId);
        WriteContext(WriteContext &&rhs);
        ~WriteContext();
        WriteContext &operator=(WriteContext &&rhs);
        void buildFieldPaths(const DocumentType &docType);
        void add(AttributeVector &attr);
        uint32_t getExecutorId() const { return _executorId; }
        const std::vector<WriteField> &getFields() const { return _fields; }
        bool hasStructFieldAttribute() const { return _hasStructFieldAttribute; }
    };
private:
    std::vector<WriteContext> _writeContexts;
    const DataType           *_dataType;
    bool                      _hasStructFieldAttribute;

    void setupWriteContexts();
    void buildFieldPaths(const DocumentType &docType, const DataType *dataType);
    void internalPut(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                     bool immediateCommit, bool allAttributes, OnWriteDoneType onWriteDone);
    void internalRemove(SerialNum serialNum, DocumentIdT lid,
                        bool immediateCommit, OnWriteDoneType onWriteDone);

public:
    AttributeWriter(const proton::IAttributeManager::SP &mgr);
    ~AttributeWriter();

    /**
     * Implements IAttributeWriter.
     */
    std::vector<search::AttributeVector *>
    getWritableAttributes() const override;
    search::AttributeVector *
    getWritableAttribute(const vespalib::string &name) const override;
    void put(SerialNum serialNum, const Document &doc, DocumentIdT lid,
             bool immediateCommit, OnWriteDoneType onWriteDone) override;
    void remove(SerialNum serialNum, DocumentIdT lid,
                bool immediateCommit, OnWriteDoneType onWriteDone) override;
    void remove(const LidVector &lidVector, SerialNum serialNum,
                bool immediateCommit, OnWriteDoneType onWriteDone) override;
    void update(SerialNum serialNum, const DocumentUpdate &upd, DocumentIdT lid,
                bool immediateCommit, OnWriteDoneType onWriteDone, IFieldUpdateCallback & onUpdate) override;
    void update(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                bool immediateCommit, OnWriteDoneType onWriteDone) override;
    void heartBeat(SerialNum serialNum) override;
    void compactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum) override;
    const proton::IAttributeManager::SP &getAttributeManager() const override {
        return _mgr;
    }
    void forceCommit(SerialNum serialNum, OnWriteDoneType onWriteDone) override;

    void onReplayDone(uint32_t docIdLimit) override;
    bool hasStructFieldAttribute() const override;
};

} // namespace proton

