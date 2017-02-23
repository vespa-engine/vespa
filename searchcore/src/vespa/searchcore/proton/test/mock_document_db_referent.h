// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/reference/i_document_db_referent.h>

namespace search {
class AttributeVector;
class IGidToLidMapperFactory;
}

namespace proton {
namespace test {

/**
 * Mock of the IDocumentDBReferent interface used for unit testing.
 */
struct MockDocumentDBReferent : public IDocumentDBReferent {
    using SP = std::shared_ptr<MockDocumentDBReferent>;
    virtual std::shared_ptr<search::AttributeVector> getAttribute(vespalib::stringref) override {
        return std::shared_ptr<search::AttributeVector>();
    }
    virtual std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() override {
        return std::shared_ptr<search::IGidToLidMapperFactory>();
    }
};

}
}
