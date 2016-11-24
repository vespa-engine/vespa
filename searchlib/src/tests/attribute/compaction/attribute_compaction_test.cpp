// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/integerbase.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_compaction_test");

using search::IntegerAttribute;
using search::AttributeVector;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;

using AttributePtr = AttributeVector::SP;
using AttributeStatus = search::attribute::Status;

namespace
{

template <typename VectorType>
bool is(AttributePtr &v)
{
    return dynamic_cast<VectorType *>(v.get());
}

template <typename VectorType>
VectorType &as(AttributePtr &v)
{
    return dynamic_cast<VectorType &>(*v);
}

void populateAttribute(IntegerAttribute &v, uint32_t docIdLimit)
{
    for(uint32_t docId = 0; docId < docIdLimit; ++docId) {
        uint32_t checkDocId = 0;
        EXPECT_TRUE(v.addDoc(checkDocId));
        EXPECT_EQUAL(docId, checkDocId);
        v.clearDoc(docId);
        for (size_t vi = 0; vi <= 40; ++vi) {
            EXPECT_TRUE(v.append(docId, 42, 1) );
        }
    }
    v.commit(true);
    v.incGeneration();
}

void populateAttribute(AttributePtr &v, uint32_t docIdLimit)
{
    if (is<IntegerAttribute>(v)) {
        populateAttribute(as<IntegerAttribute>(v), docIdLimit);
    }
}

void cleanAttribute(AttributeVector &v, uint32_t docIdLimit)
{
    for (uint32_t docId = 0; docId < docIdLimit; ++docId) {
        v.clearDoc(docId);
    }
    v.commit(true);
    v.incGeneration();
}

}

class Fixture {
public:
    AttributePtr _v;

    Fixture(Config cfg)
        : _v()
    { _v = search::AttributeFactory::createAttribute("test", cfg); }
    ~Fixture() { }
    void populate(uint32_t docIdLimit) { populateAttribute(_v, docIdLimit); }
    void clean(uint32_t docIdLimit) { cleanAttribute(*_v, docIdLimit); }
    AttributeStatus getStatus() { _v->commit(true); return _v->getStatus(); }
    AttributeStatus getStatus(const vespalib::string &prefix) {
        AttributeStatus status(getStatus());
        LOG(info, "status %s: used=%zu, dead=%zu, onHold=%zu",
            prefix.c_str(), status.getUsed(), status.getDead(), status.getOnHold());
        return status;
    }
};

TEST_F("Test that compaction of integer array attribute reduces memory usage", Fixture({ BasicType::INT64, CollectionType::ARRAY }))
{
    f.populate(3000);
    AttributeStatus beforeStatus = f.getStatus("before");
    f.clean(2000);
    AttributeStatus afterStatus = f.getStatus("after");
    EXPECT_LESS(afterStatus.getUsed(), beforeStatus.getUsed());
}

TEST_MAIN() { TEST_RUN_ALL(); }
