// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::DumpVisitorSingle
 * @ingroup visitors
 *
 * @brief A dump visitor is a visitor that sends documents to the client.
 * Each document is sent as a single message
 *
 */
#pragma once

#include "visitor.h"

namespace storage {

class DumpVisitorSingle : public Visitor {
public:
    DumpVisitorSingle(StorageComponent&,
                      const vdslib::Parameters& params);

private:
    void handleDocuments(const document::BucketId&, DocEntryList&, HitCounter&) override;
};

struct DumpVisitorSingleFactory : public VisitorFactory {

    std::shared_ptr<VisitorEnvironment>
    makeVisitorEnvironment(StorageComponent&) override {
        return std::make_shared<VisitorEnvironment>();
    };

    Visitor*
    makeVisitor(StorageComponent& c, VisitorEnvironment&, const vdslib::Parameters& params) override {
        return new DumpVisitorSingle(c, params);
    }
};

}
