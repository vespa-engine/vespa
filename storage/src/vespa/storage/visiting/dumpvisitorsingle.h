// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::DumpVisitorSingle
 * @ingroup visitors
 *
 * @brief A dump visitor is a visitor that sends documents to the client.
 * Each document is sent as a single message
 *
 */
#pragma once

#include <vespa/storage/visiting/visitor.h>

namespace storage {

class DumpVisitorSingle : public Visitor {
public:
    DumpVisitorSingle(StorageComponent&,
                      const vdslib::Parameters& params);

private:
    void handleDocuments(const document::BucketId&,
                         std::vector<spi::DocEntry::UP>&,
                         HitCounter&);
};

struct DumpVisitorSingleFactory : public VisitorFactory {

    VisitorEnvironment::UP
    makeVisitorEnvironment(StorageComponent&) {
        return VisitorEnvironment::UP(new VisitorEnvironment);
    };

    Visitor*
    makeVisitor(StorageComponent& c, VisitorEnvironment&,
                const vdslib::Parameters& params)
    {
        return new DumpVisitorSingle(c, params);
    }
};

}



