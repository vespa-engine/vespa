// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"

namespace storage {

/**
 * A visitor instance that is intended to be used for background reindexing.
 * Only meant to be run alongside distributor-level bucket locking support
 * that prevents concurrent writes to documents in the visited bucket.
 *
 * The bucket lock is explicitly bypassed by the Puts sent by the visitor
 * by having all these be augmented with a special TaS string that is
 * recognized by the distributor.
 */
class ReindexingVisitor : public Visitor {
public:
    explicit ReindexingVisitor(StorageComponent& component);
    ~ReindexingVisitor() override = default;

private:
    void handleDocuments(const document::BucketId&, DocEntryList&, HitCounter&) override;
    bool remap_docapi_message_error_code(api::ReturnCode& in_out_code) override;
    vespalib::string make_lock_access_token() const;
};

struct ReindexingVisitorFactory : public VisitorFactory {
    VisitorEnvironment::UP makeVisitorEnvironment(StorageComponent&) override {
        return std::make_unique<VisitorEnvironment>();
    };

    Visitor* makeVisitor(StorageComponent& c, VisitorEnvironment&, const vdslib::Parameters&) override {
        return new ReindexingVisitor(c);
    }
};

}
