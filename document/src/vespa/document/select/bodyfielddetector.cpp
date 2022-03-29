// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bodyfielddetector.h"
#include "valuenodes.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>

namespace document::select {

void
BodyFieldDetector::detectFieldType(const FieldValueNode *expr, const DocumentType &type)
{
    if (type.getName() != expr->getDocType()) {
        return;
    }
    try {
        FieldPath path;
        type.buildFieldPath(path, expr->getFieldName());
        if ( ! path.empty() ) {
            foundHeaderField = true;
        }
    } catch (FieldNotFoundException &) {
    }
}


void
BodyFieldDetector::visitFieldValueNode(const FieldValueNode& expr)
{
    _repo.forEachDocumentType([&](const DocumentType &type) {
        detectFieldType(&expr, type);
    });
}


}
