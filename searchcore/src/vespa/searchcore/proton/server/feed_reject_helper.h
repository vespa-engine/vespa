// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document {
    class FieldValue;
    class DocumentUpdate;
    class ValueUpdate;
}

namespace proton {

class FeedOperation;
class UpdateOperation;

class FeedRejectHelper {
public:
    static bool isRejectableFeedOperation(const FeedOperation & op);
    // Public only for testing
    static bool isFixedSizeSingleValue(const document::FieldValue & fv);
    static bool mustReject(const document::ValueUpdate & valueUpdate);
    static bool mustReject(const document::DocumentUpdate & documentUpdate);
    static bool mustReject(const UpdateOperation & updateOperation);
};

}
