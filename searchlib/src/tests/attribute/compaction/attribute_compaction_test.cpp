// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/address_space_usage.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_compaction_test");

using search::IntegerAttribute;
using search::AttributeVector;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using vespalib::AddressSpace;

using AttributePtr = AttributeVector::SP;
using AttributeStatus = search::attribute::Status;

namespace
{

struct DocIdRange {
    uint32_t docIdStart;
    uint32_t docIdLimit;
    DocIdRange(uint32_t docIdStart_, uint32_t docIdLimit_)
        : docIdStart(docIdStart_),
          docIdLimit(docIdLimit_)
    {
    }
    uint32_t begin() { return docIdStart; }
    uint32_t end() { return docIdLimit; }
    uint32_t size() { return end() - begin(); }
};


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

void cleanAttribute(AttributeVector &v, DocIdRange range)
{
    for (uint32_t docId = range.begin(); docId < range.end(); ++docId) {
        v.clearDoc(docId);
    }
    v.commit(true);
    v.incGeneration();
}

DocIdRange addAttributeDocs(AttributePtr &v, uint32_t numDocs)
{
    uint32_t startDoc = 0;
    uint32_t lastDoc = 0;
    EXPECT_TRUE(v->addDocs(startDoc, lastDoc, numDocs));
    EXPECT_EQUAL(startDoc + numDocs - 1, lastDoc);
    DocIdRange range(startDoc, startDoc + numDocs);
    cleanAttribute(*v, range);
    return range;
}

void populateAttribute(IntegerAttribute &v, DocIdRange range, uint32_t values)
{
    for(uint32_t docId = range.begin(); docId < range.end(); ++docId) {
        v.clearDoc(docId);
        for (uint32_t vi = 0; vi <= values; ++vi) {
            EXPECT_TRUE(v.append(docId, 42, 1) );
        }
        if ((docId % 100) == 0) {
            v.commit();
        }
    }
    v.commit(true);
    v.incGeneration();
}

void populateAttribute(AttributePtr &v, DocIdRange range, uint32_t values)
{
    if (is<IntegerAttribute>(v)) {
        populateAttribute(as<IntegerAttribute>(v), range, values);
    }
}

void hammerAttribute(IntegerAttribute &v, DocIdRange range, uint32_t count)
{
    uint32_t work = 0;
    for (uint32_t i = 0; i < count; ++i) {
        for (uint32_t docId = range.begin(); docId < range.end(); ++docId) {
            v.clearDoc(docId);
            EXPECT_TRUE(v.append(docId, 42, 1));
        }
        work += range.size();
        if (work >= 100000) {
            v.commit(true);
            work = 0;
        } else {
            v.commit();
        }
    }
    v.commit(true);
    v.incGeneration();
}

void hammerAttribute(AttributePtr &v, DocIdRange range, uint32_t count)
{
    if (is<IntegerAttribute>(v)) {
        hammerAttribute(as<IntegerAttribute>(v), range, count);
    }
}

Config compactAddressSpaceAttributeConfig(bool enableAddressSpaceCompact)
{
    Config cfg(BasicType::INT8, CollectionType::ARRAY);
    cfg.setCompactionStrategy({ 1.0, (enableAddressSpaceCompact ? 0.2 : 1.0) });
    return cfg;
}

}

class Fixture {
public:
    AttributePtr _v;

    Fixture(Config cfg)
        : _v()
    { _v = search::AttributeFactory::createAttribute("test", cfg); }
    ~Fixture() { }
    DocIdRange addDocs(uint32_t numDocs) { return addAttributeDocs(_v, numDocs); }
    void populate(DocIdRange range, uint32_t values) { populateAttribute(_v, range, values); }
    void hammer(DocIdRange range, uint32_t count) { hammerAttribute(_v, range, count); }
    void clean(DocIdRange range) { cleanAttribute(*_v, range); }
    AttributeStatus getStatus() { _v->commit(true); return _v->getStatus(); }
    AttributeStatus getStatus(const vespalib::string &prefix) {
        AttributeStatus status(getStatus());
        LOG(info, "status %s: used=%" PRIu64 ", dead=%" PRIu64 ", onHold=%" PRIu64,
            prefix.c_str(), status.getUsed(), status.getDead(), status.getOnHold());
        return status;
    }
    const Config &getConfig() const { return _v->getConfig(); }
    AddressSpace getMultiValueAddressSpaceUsage() const {return _v->getAddressSpaceUsage().multiValueUsage(); }
    AddressSpace getMultiValueAddressSpaceUsage(const vespalib::string &prefix) {
        AddressSpace usage(getMultiValueAddressSpaceUsage());
        LOG(info, "address space usage %s: used=%zu, dead=%zu, limit=%zu, usage=%12.8f",
            prefix.c_str(), usage.used(), usage.dead(), usage.limit(), usage.usage());
        return usage;
    }
};

TEST_F("Test that compaction of integer array attribute reduces memory usage", Fixture({ BasicType::INT64, CollectionType::ARRAY }))
{
    DocIdRange range1 = f.addDocs(2000);
    DocIdRange range2 = f.addDocs(1000);
    f.populate(range1, 40);
    f.populate(range2, 40);
    AttributeStatus beforeStatus = f.getStatus("before");
    f.clean(range1);
    AttributeStatus afterStatus = f.getStatus("after");
    EXPECT_LESS(afterStatus.getUsed(), beforeStatus.getUsed());
}

void
populate_and_hammer(Fixture& f, bool take_attribute_guard)
{
    DocIdRange range1 = f.addDocs(1000);
    DocIdRange range2 = f.addDocs(1000);
    if (take_attribute_guard) {
        {
            // When attribute guard is held free lists will not be used in the hammer step.
            search::AttributeGuard guard(f._v);
            f.populate(range1, 1000);
            f.hammer(range2, 101);
        }
        f._v->commit(true);
        f._v->commit();
    } else {
        f.populate(range1, 1000);
        f.hammer(range2, 101);
    }
}

TEST_F("Address space usage (dead) increases significantly when free lists are NOT used (compaction configured off)",
       Fixture(compactAddressSpaceAttributeConfig(false)))
{
    populate_and_hammer(f, true);
    AddressSpace afterSpace = f.getMultiValueAddressSpaceUsage("after");
    // 100 * 1000 dead arrays due to new values for docids
    // 1 reserved array accounted as dead
    EXPECT_EQUAL(100001u, afterSpace.dead());
}

TEST_F("Address space usage (dead) increases only slightly when free lists are used (compaction configured off)",
       Fixture(compactAddressSpaceAttributeConfig(false)))
{
    populate_and_hammer(f, false);
    AddressSpace afterSpace = f.getMultiValueAddressSpaceUsage("after");
    // Only 1000 dead arrays (due to new values for docids) as free lists are used.
    // 1 reserved array accounted as dead
    EXPECT_EQUAL(1001u, afterSpace.dead());
}

TEST_F("Compaction limits address space usage (dead) when free lists are NOT used",
       Fixture(compactAddressSpaceAttributeConfig(true)))
{
    populate_and_hammer(f, true);
    AddressSpace afterSpace = f.getMultiValueAddressSpaceUsage("after");
    // DEAD_ARRAYS_SLACK in multi value mapping is is 64k
    EXPECT_GREATER(65536u, afterSpace.dead());
}

TEST_F("Compaction is not executed when free lists are used",
       Fixture(compactAddressSpaceAttributeConfig(true)))
{
    populate_and_hammer(f, false);
    AddressSpace afterSpace = f.getMultiValueAddressSpaceUsage("after");
    // Only 1000 dead arrays (due to new values for docids) as free lists are used.
    // 1 reserved array accounted as dead
    EXPECT_EQUAL(1001u, afterSpace.dead());
}

TEST_MAIN() { TEST_RUN_ALL(); }
