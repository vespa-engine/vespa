// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_attribute_manager.h"
#include <vespa/searchcore/proton/feedoperation/lidvectorcontext.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/searchlib/common/commit_param.h>

namespace vespalib { class IDestructorCallback; }
namespace document {
    class DocumentUpdate;
    class Document;
}

namespace proton {

struct IFieldUpdateCallback;

/**
 * Interface for an attribute writer that handles writes in form of put, update and remove
 * to an underlying set of attribute vectors.
 */
class IAttributeWriter {
public:
    using UP = std::unique_ptr<IAttributeWriter>;
    using SP = std::shared_ptr<IAttributeWriter>;
    using LidVector = LidVectorContext::LidVector;
    using SerialNum = search::SerialNum;
    using CommitParam = search::CommitParam;
    using DocumentIdT = search::DocumentIdT;
    using DocumentUpdate = document::DocumentUpdate;
    using Document = document::Document;
    using OnWriteDoneType = const std::shared_ptr<vespalib::IDestructorCallback> &;

    virtual ~IAttributeWriter() = default;

    virtual std::vector<search::AttributeVector *> getWritableAttributes() const = 0;
    virtual search::AttributeVector *getWritableAttribute(const vespalib::string &attrName) const = 0;
    virtual void put(SerialNum serialNum, const Document &doc, DocumentIdT lid, OnWriteDoneType onWriteDone) = 0;
    virtual void remove(SerialNum serialNum, DocumentIdT lid, OnWriteDoneType onWriteDone) = 0;
    virtual void remove(const LidVector &lidVector, SerialNum serialNum, OnWriteDoneType onWriteDone) = 0;
    /**
     * Update the underlying attributes based on the content of the given DocumentUpdate.
     * The OnWriteDoneType instance should ensure the lifetime of the given DocumentUpdate instance.
     */
    virtual void update(SerialNum serialNum, const DocumentUpdate &upd, DocumentIdT lid,
                        OnWriteDoneType onWriteDone, IFieldUpdateCallback & onUpdate) = 0;
    /*
     * Update the underlying struct field attributes based on updated document.
     */
    virtual void update(SerialNum serialNum, const Document &doc, DocumentIdT lid, OnWriteDoneType onWriteDone) = 0;
    virtual void heartBeat(SerialNum serialNum) = 0;
    /**
     * Compact the lid space of the underlying attribute vectors.
     */
    virtual void compactLidSpace(uint32_t wantedLidLimit, SerialNum serialNum) = 0;
    virtual const proton::IAttributeManager::SP &getAttributeManager() const = 0;

    /**
     * Commit all underlying attribute vectors with the given param.
     */
    virtual void forceCommit(const CommitParam & param, OnWriteDoneType onWriteDone) = 0;

    virtual void onReplayDone(uint32_t docIdLimit) = 0;
    virtual bool hasStructFieldAttribute() const = 0;
};

} // namespace proton

