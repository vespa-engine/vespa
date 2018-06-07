// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_attribute_manager.h"
#include <vespa/searchcore/proton/feedoperation/lidvectorcontext.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/searchlib/common/serialnum.h>

namespace search { class IDestructorCallback; }
namespace document {
    class DocumentUpdate;
    class Document;
}

namespace proton {

class IFieldUpdateCallback;

/**
 * Interface for an attribute writer that handles writes in form of put, update and remove
 * to an underlying set of attribute vectors.
 */
class IAttributeWriter {
public:
    typedef std::unique_ptr<IAttributeWriter> UP;
    typedef std::shared_ptr<IAttributeWriter> SP;
    typedef std::vector<search::AttributeGuard> AttributeGuardList;
    typedef LidVectorContext::LidVector LidVector;
    typedef search::SerialNum SerialNum;
    typedef search::DocumentIdT DocumentIdT;
    typedef document::DocumentUpdate DocumentUpdate;
    typedef document::Document Document;
    using OnWriteDoneType = const std::shared_ptr<search::IDestructorCallback> &;

    virtual ~IAttributeWriter() {}

    virtual std::vector<search::AttributeVector *> getWritableAttributes() const = 0;
    virtual search::AttributeVector *getWritableAttribute(const vespalib::string &attrName) const = 0;
    virtual void put(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                     bool immediateCommit, OnWriteDoneType onWriteDone) = 0;
    virtual void remove(SerialNum serialNum, DocumentIdT lid, bool immediateCommit,
                        OnWriteDoneType onWriteDone) = 0;
    virtual void remove(const LidVector &lidVector, SerialNum serialNum,
                        bool immediateCommit, OnWriteDoneType onWriteDone) = 0;
    /**
     * Update the underlying attributes based on the content of the given DocumentUpdate.
     * The OnWriteDoneType instance should ensure the lifetime of the given DocumentUpdate instance.
     */
    virtual void update(SerialNum serialNum, const DocumentUpdate &upd, DocumentIdT lid,
                        bool immediateCommit, OnWriteDoneType onWriteDone, IFieldUpdateCallback & onUpdate) = 0;
    /*
     * Update the underlying struct field attributes based on updated document.
     */
    virtual void update(SerialNum serialNum, const Document &doc, DocumentIdT lid,
                        bool immediateCommit, OnWriteDoneType onWriteDone) = 0;
    virtual void heartBeat(SerialNum serialNum) = 0;
    /**
     * Compact the lid space of the underlying attribute vectors.
     */
    virtual void compactLidSpace(uint32_t wantedLidLimi, SerialNum serialNum) = 0;
    virtual const proton::IAttributeManager::SP &getAttributeManager() const = 0;

    /**
     * Commit all underlying attribute vectors with the given serial number.
     */
    virtual void forceCommit(SerialNum serialNum, OnWriteDoneType onWriteDone) = 0;

    virtual void onReplayDone(uint32_t docIdLimit) = 0;

    virtual bool hasStructFieldAttribute() const = 0;
};

} // namespace proton

