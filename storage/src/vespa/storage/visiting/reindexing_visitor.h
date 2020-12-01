// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"

namespace storage {

class ReindexingVisitor : public Visitor {
public:
    explicit ReindexingVisitor(StorageComponent& component);
    ~ReindexingVisitor() override = default;

private:
    void handleDocuments(const document::BucketId&, std::vector<spi::DocEntry::UP>&, HitCounter&) override;
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
