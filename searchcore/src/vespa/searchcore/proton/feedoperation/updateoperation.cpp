// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
                                 Timestamp timestamp, DocumentUpdate::SP upd)
    : DocumentOperation(type, bucketId, timestamp),
      _upd(std::move(upd))
{
}


UpdateOperation::UpdateOperation(const BucketId &bucketId, Timestamp timestamp, DocumentUpdate::SP upd)
    : UpdateOperation(FeedOperation::UPDATE, bucketId, timestamp, std::move(upd))
{
}

UpdateOperation::~UpdateOperation() = default;

void
UpdateOperation::serializeUpdate(vespalib::nbostream &os) const
{
    assert(getType() == UPDATE);
     _upd->serializeHEAD(os);
}

void
UpdateOperation::deserializeUpdate(vespalib::nbostream && is, const document::DocumentTypeRepo &repo)
{
    _upd = DocumentUpdate::createHEAD(repo, std::move(is));
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
        deserializeUpdate(std::move(is), repo);
    } catch (document::DocumentTypeNotFoundException &e) {
        LOG(warning, "Failed deserialize update operation using unknown document type '%s'",
            e.getDocumentTypeName().c_str());
        // Ignore this piece of data
        is.clear();
    }
}

void
UpdateOperation::verifyUpdate(const DocumentTypeRepo &repo)
{
    vespalib::nbostream stream;
    serializeUpdate(stream);
    deserializeUpdate(std::move(stream), repo);
    _upd->eagerDeserialize();  // Will trigger exceptions if incompatible
}

vespalib::string
UpdateOperation::toString() const {
    return make_string("%s(%s, %s)",
                       ((getType() == FeedOperation::UPDATE_42) ? "Update42" : "Update"),
                       _upd.get() ? _upd->getId().getScheme().toString().c_str() : "NULL",
                       docArgsToString().c_str());
}

} // namespace proton
