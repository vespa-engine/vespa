// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    typedef std::vector<std::unique_ptr<FieldPath> > AttributeFieldPaths;
    const IAttributeManager::SP _mgr;
    AttributeFieldPaths         _fieldPaths;
    const DataType             *_dataType;
    vespalib::string            _fieldPathsDocTypeName;
    search::ISequencedTaskExecutor &_attributeFieldWriter;
    const std::vector<search::AttributeVector *> &_writableAttributes;

    void buildFieldPath(const DocumentType &docType, const DataType *dataType);
    void internalPut(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                     bool immediateCommit, OnWriteDoneType onWriteDone);
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
                bool immediateCommit, OnWriteDoneType onWriteDone) override;
    void heartBeat(SerialNum serialNum) override;
    void compactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum) override;
    const proton::IAttributeManager::SP &getAttributeManager() const override {
        return _mgr;
    }
    void commit(SerialNum serialNum, OnWriteDoneType onWriteDone) override;

    virtual void onReplayDone(uint32_t docIdLimit) override;
};

} // namespace proton

