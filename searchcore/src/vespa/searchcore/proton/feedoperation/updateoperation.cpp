// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "updateoperation.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.feedoperation.updateoperation");


using document::BucketId;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using storage::spi::Timestamp;
using vespalib::make_string;

namespace proton {

UpdateOperation::UpdateOperation()
    : DocumentOperation(FeedOperation::UPDATE),
      _upd()
{
}


UpdateOperation::UpdateOperation(const BucketId &bucketId,
                                 const Timestamp &timestamp,
                                 const DocumentUpdate::SP &upd)
    : DocumentOperation(FeedOperation::UPDATE,
                        bucketId,
                        timestamp),
      _upd(upd)
{
}


UpdateOperation::UpdateOperation(const document::BucketId &bucketId,
                                 const storage::spi::Timestamp &timestamp,
                                 const document::DocumentUpdate::SP &upd,
                                 SerialNum serialNum,
                                 DbDocumentId dbdId,
                                 DbDocumentId prevDbdId)
    : DocumentOperation(FeedOperation::UPDATE,
                        bucketId,
                        timestamp,
                        serialNum,
                        dbdId,
                        prevDbdId),
      _upd(upd)
{
}


void
UpdateOperation::serialize(vespalib::nbostream &os) const
{
    assertValidBucketId(_upd->getId());
    DocumentOperation::serialize(os);
    _upd->serialize42(os);
}


void
UpdateOperation::deserialize(vespalib::nbostream &is,
                             const DocumentTypeRepo &repo)
{
    DocumentOperation::deserialize(is, repo);
    document::ByteBuffer buf(is.peek(), is.size());
    try {
        DocumentUpdate::SP update(new DocumentUpdate(repo, buf,
                                                     DocumentUpdate::
                                                     SerializeVersion::
                                                     SERIALIZE_42));
        is.adjustReadPos(buf.getPos());
        _upd = update;
    } catch (document::DocumentTypeNotFoundException &e) {
        LOG(warning, "Failed deserialize update operation using unknown document type '%s'",
            e.getDocumentTypeName().c_str());
        // Ignore this piece of data
        is.clear();
    }
}

vespalib::string UpdateOperation::toString() const {
    return make_string("Update(%s, %s)",
                       _upd.get() ?
                       _upd->getId().getScheme().toString().c_str() : "NULL",
                       docArgsToString().c_str());
}
} // namespace proton
