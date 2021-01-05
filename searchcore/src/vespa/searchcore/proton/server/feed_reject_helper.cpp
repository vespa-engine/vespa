// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feed_reject_helper.h"
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/fieldvalue/boolfieldvalue.h>
#include <vespa/document/fieldvalue/numericfieldvalue.h>

using namespace document;

namespace proton {

bool
FeedRejectHelper::isFixedSizeSingleValue(const document::FieldValue & fv) {
    return fv.inherits(BoolFieldValue::classId) || fv.inherits(NumericFieldValueBase::classId);
}

bool
FeedRejectHelper::mustReject(const document::ValueUpdate & valueUpdate) {
    using namespace document;
    switch (valueUpdate.getType()) {
        case ValueUpdate::Add:
        case ValueUpdate::TensorAddUpdate:
        case ValueUpdate::TensorModifyUpdate:
        case ValueUpdate::Map:
            return true;
        case ValueUpdate::Assign: {
            const auto & assign = dynamic_cast<const AssignValueUpdate &>(valueUpdate);
            if (assign.hasValue()) {
                if ( ! isFixedSizeSingleValue(assign.getValue())) {
                    return true;
                }
            }
        }
        default:
            break;
    }
    return false;
}

bool
FeedRejectHelper::mustReject(const DocumentUpdate & documentUpdate) {
    for (const auto & update : documentUpdate.getUpdates()) {
        for (const auto & valueUpdate : update.getUpdates()) {
            if (mustReject(*valueUpdate)) {
                return true;
            }
        }
    }
    return ! documentUpdate.getFieldPathUpdates().empty();
}

bool
FeedRejectHelper::mustReject(const UpdateOperation & updateOperation) {
    using namespace document;
    if (updateOperation.getUpdate()) {
        return mustReject(*updateOperation.getUpdate());
    }
    return false;
}

bool
FeedRejectHelper::isRejectableFeedOperation(const FeedOperation & op)
{
    FeedOperation::Type type = op.getType();
    if (type == FeedOperation::PUT) {
        return true;
    } else if (type == FeedOperation::UPDATE_42 || type == FeedOperation::UPDATE) {
        return mustReject(dynamic_cast<const UpdateOperation &>(op));
    }
    return false;
}

}
