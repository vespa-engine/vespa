// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "updateoperation.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/update/documentupdate.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.feedoperation.updateoperation");


using document::BucketId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace proton {

UpdateOperation::UpdateOperation()
    : UpdateOperation(FeedOperation::UPDATE)
{
}

UpdateOperation::UpdateOperation(Type type)
    : DocumentOperation(type),
      _upd()
{
}


UpdateOperation::UpdateOperation(Type type, const BucketId &bucketId,
                                 const Timestamp &timestamp, const DocumentUpdate::SP &upd)
    : DocumentOperation(type, bucketId, timestamp),
      _upd(upd)
{
}


UpdateOperation::UpdateOperation(const BucketId &bucketId, const Timestamp &timestamp, const DocumentUpdate::SP &upd)
    : UpdateOperation(FeedOperation::UPDATE, bucketId, timestamp, upd)
{
}

void
UpdateOperation::serializeUpdate(vespalib::nbostream &os) const
{
    assert(getType() == UPDATE);
     _upd->serializeHEAD(os);
}

void
UpdateOperation::deserializeUpdate(vespalib::nbostream &is, const document::DocumentTypeRepo &repo)
{
    document::ByteBuffer buf(is.peek(), is.size());
    DocumentUpdate::UP update = (getType() == UPDATE_42) ? DocumentUpdate::create42(repo, buf) : DocumentUpdate::createHEAD(repo, buf);
    is.adjustReadPos(buf.getPos());
    _upd = std::move(update);
}

void
UpdateOperation::serialize(vespalib::nbostream &os) const
{
    assertValidBucketId(_upd->getId());
    DocumentOperation::serialize(os);
    serializeUpdate(os);
}


void
UpdateOperation::deserialize(vespalib::nbostream &is, const DocumentTypeRepo &repo)
{
    DocumentOperation::deserialize(is, repo);
    try {
        deserializeUpdate(is, repo);
    } catch (document::DocumentTypeNotFoundException &e) {
        LOG(warning, "Failed deserialize update operation using unknown document type '%s'",
            e.getDocumentTypeName().c_str());
        // Ignore this piece of data
        is.clear();
    }
}

void
UpdateOperation::deserializeUpdate(const DocumentTypeRepo &repo)
{
    vespalib::nbostream stream;
    serializeUpdate(stream);
    deserializeUpdate(stream, repo);
}

vespalib::string UpdateOperation::toString() const {
    return make_string("%s(%s, %s)",
                       ((getType() == FeedOperation::UPDATE_42) ? "Update42" : "Update"),
                       _upd.get() ?
                       _upd->getId().getScheme().toString().c_str() : "NULL",
                       docArgsToString().c_str());
}

} // namespace proton
