// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reference/i_document_db_reference.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_registrator.h>

namespace search {
class AttributeVector;
class IGidToLidMapperFactory;
}

namespace proton {
namespace test {

/**
 * Mock of the IDocumentDBReference interface used for unit testing.
 */
struct MockDocumentDBReference : public IDocumentDBReference {
    using SP = std::shared_ptr<MockDocumentDBReference>;
    virtual std::shared_ptr<search::AttributeVector> getAttribute(vespalib::stringref) override {
        return std::shared_ptr<search::AttributeVector>();
    }
    virtual std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() override {
        return std::shared_ptr<search::IGidToLidMapperFactory>();
    }
    virtual std::unique_ptr<GidToLidChangeRegistrator> makeGidToLidChangeRegistrator(const vespalib::string &) override {
        return std::unique_ptr<GidToLidChangeRegistrator>();
    }
};

}
}
