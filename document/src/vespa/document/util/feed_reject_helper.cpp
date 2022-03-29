// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feed_reject_helper.h"
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/fieldvalue/boolfieldvalue.h>

namespace document {

bool
FeedRejectHelper::isFixedSizeSingleValue(const document::FieldValue & fv) {
    return fv.isFixedSizeSingleValue();
}

bool
FeedRejectHelper::mustReject(const document::ValueUpdate & valueUpdate) {
    using namespace document;
    switch (valueUpdate.getType()) {
        case ValueUpdate::Add:
        case ValueUpdate::TensorAdd:
        case ValueUpdate::TensorModify:
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

}
