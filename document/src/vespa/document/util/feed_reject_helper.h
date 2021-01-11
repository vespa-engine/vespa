// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document {

class FieldValue;
class DocumentUpdate;
class ValueUpdate;


/**
 * Tells wether an operation should be blocked when resourcelimits have been reached.
 * It looks at the operation type and also the content if it is an 'update' operation.
 */
class FeedRejectHelper {
public:
    static bool isFixedSizeSingleValue(const FieldValue & fv);
    static bool mustReject(const ValueUpdate & valueUpdate);
    static bool mustReject(const DocumentUpdate & documentUpdate);
};

}
