// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reference/i_document_db_reference.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_registrator.h>

namespace search::attribute { class ReadableAttributeVector; }
namespace search { class IGidToLidMapperFactory; }

namespace proton::test {

/**
 * Mock of the IDocumentDBReference interface used for unit testing.
 */
struct MockDocumentDBReference : public IDocumentDBReference {
    using SP = std::shared_ptr<MockDocumentDBReference>;
    std::shared_ptr<search::attribute::ReadableAttributeVector> getAttribute(std::string_view) override {
        return std::shared_ptr<search::attribute::ReadableAttributeVector>();
    }
    std::shared_ptr<const search::IDocumentMetaStoreContext> getDocumentMetaStore() const override {
        return std::shared_ptr<const search::IDocumentMetaStoreContext>();
    }
    std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() override {
        return std::shared_ptr<search::IGidToLidMapperFactory>();
    }
    std::unique_ptr<GidToLidChangeRegistrator> makeGidToLidChangeRegistrator(const std::string &) override {
        return std::unique_ptr<GidToLidChangeRegistrator>();
    }
};

}
