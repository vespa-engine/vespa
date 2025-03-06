// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchlib/attribute/address_space_usage.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_compaction_test");

using search::IntegerAttribute;
using search::AttributeVector;
using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using vespalib::AddressSpace;
using vespalib::datastore::CompactionStrategy;

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
    EXPECT_EQ(startDoc + numDocs - 1, lastDoc);
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
    cfg.setCompactionStrategy({ 1.0f, (enableAddressSpaceCompact ? 0.2f : 1.0f) });
    return cfg;
}

}

double
calc_alloc_waste(const AttributeStatus& status)
{
    return ((double)(status.getAllocated() - status.getUsed())) / status.getAllocated();
}

class Fixture {
public:
    AttributePtr _v;
    size_t _reserved_multi_value_address_space;

    Fixture(Config cfg)
        : _v(),
          _reserved_multi_value_address_space(0)
    {
        _v = search::AttributeFactory::createAttribute("test", cfg);
        // 1 reserved array accounted as dead. Scaling applied when reporting usage (due to capped buffer sizes)
        _reserved_multi_value_address_space = getMultiValueAddressSpaceUsage().dead();
    }
    ~Fixture() { }
    DocIdRange addDocs(uint32_t numDocs) { return addAttributeDocs(_v, numDocs); }
    void populate(DocIdRange range, uint32_t values) { populateAttribute(_v, range, values); }
    void hammer(DocIdRange range, uint32_t count) { hammerAttribute(_v, range, count); }
    void clean(DocIdRange range) { cleanAttribute(*_v, range); }
    AttributeStatus getStatus() { _v->commit(true); return _v->getStatus(); }
    AttributeStatus getStatus(const std::string &prefix) {
        AttributeStatus status(getStatus());
        LOG(info, "status %s: allocated=%" PRIu64 ", used=%" PRIu64 ", dead=%" PRIu64 ", onHold=%" PRIu64 ", waste=%f",
            prefix.c_str(), status.getAllocated(), status.getUsed(), status.getDead(), status.getOnHold(),
            calc_alloc_waste(status));
        return status;
    }
    const Config &getConfig() const { return _v->getConfig(); }
    AddressSpace getMultiValueAddressSpaceUsage() const {return _v->getAddressSpaceUsage().multi_value_usage(); }
    AddressSpace getMultiValueAddressSpaceUsage(const std::string &prefix) {
        AddressSpace usage(getMultiValueAddressSpaceUsage());
        LOG(info, "address space usage %s: used=%zu, dead=%zu, limit=%zu, usage=%12.8f",
            prefix.c_str(), usage.used(), usage.dead(), usage.limit(), usage.usage());
        return usage;
    }
    size_t reserved_multi_value_address_space() const noexcept { return _reserved_multi_value_address_space; }
};

TEST(AttributeCompactionTest, Test_that_compaction_of_integer_array_attribute_reduces_memory_usage)
{
    Fixture f({ BasicType::INT64, CollectionType::ARRAY });
    DocIdRange range1 = f.addDocs(2000);
    DocIdRange range2 = f.addDocs(1000);
    f.populate(range1, 40);
    f.populate(range2, 40);
    AttributeStatus beforeStatus = f.getStatus("before");
    f.clean(range1);
    AttributeStatus afterStatus = f.getStatus("after");
    EXPECT_LT(afterStatus.getUsed(), beforeStatus.getUsed());
}

TEST(AttributeCompactionTest, Allocated_memory_is_not_accumulated_in_an_array_attribute_when_moving_between_value_classes_when_compaction_is_active)
{
    Fixture f({BasicType::INT64, CollectionType::ARRAY});
    DocIdRange range = f.addDocs(1000);
    for (uint32_t i = 0; i < 50; ++i) {
        uint32_t values = 10 + i;
        // When moving all documents from one value class to the next,
        // all elements in the buffers of the previous value class are marked dead.
        // Those buffers will eventually be compacted. By taking the dead elements into account when
        // calculating how large the resulting compacted buffer should be,
        // we don't accumulate allocated memory as part of that process.
        f.populate(range, values);
        auto status = f.getStatus(vespalib::make_string("values=%u", values));
        EXPECT_LT(calc_alloc_waste(status), 0.68);
    }
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

TEST(AttributeCompactionTest, Address_space_usage_dead_increases_significantly_when_free_lists_are_NOT_used_and_compaction_configured_off)
{
    Fixture f(compactAddressSpaceAttributeConfig(false));
    populate_and_hammer(f, true);
    AddressSpace afterSpace = f.getMultiValueAddressSpaceUsage("after");
    // 100 * 1000 dead arrays due to new values for docids
    EXPECT_EQ(100000 + f.reserved_multi_value_address_space(), afterSpace.dead());
}

TEST(AttributeCompactionTest, Address_space_usage_dead_increases_only_slightly_when_free_lists_are_used_and_compaction_configured_off)
{
    Fixture f(compactAddressSpaceAttributeConfig(false));
    populate_and_hammer(f, false);
    AddressSpace afterSpace = f.getMultiValueAddressSpaceUsage("after");
    // Only 1000 dead arrays (due to new values for docids) as free lists are used.
    EXPECT_EQ(1000 + f.reserved_multi_value_address_space(), afterSpace.dead());
}

TEST(AttributeCompactionTest, Compaction_limits_address_space_usage_dead_when_free_lists_are_NOT_used)
{
    Fixture f(compactAddressSpaceAttributeConfig(true));
    populate_and_hammer(f, true);
    AddressSpace afterSpace = f.getMultiValueAddressSpaceUsage("after");
    EXPECT_GT(CompactionStrategy::DEAD_ADDRESS_SPACE_SLACK, afterSpace.dead());
}

TEST(AttributeCompactionTest, Compaction_is_not_executed_when_free_lists_are_used)
{
    Fixture f(compactAddressSpaceAttributeConfig(true));
    populate_and_hammer(f, false);
    AddressSpace afterSpace = f.getMultiValueAddressSpaceUsage("after");
    // Only 1000 dead arrays (due to new values for docids) as free lists are used.
    EXPECT_EQ(1000 + f.reserved_multi_value_address_space(), afterSpace.dead());
}

TEST(AttributeCompactionTest, Compaction_is_peformed_when_compaction_strategy_is_changed_to_enable_compaction)
{
    Fixture f(compactAddressSpaceAttributeConfig(false));
    populate_and_hammer(f, true);
    AddressSpace after1 = f.getMultiValueAddressSpaceUsage("after1");
    // 100 * 1000 dead arrays due to new values for docids
    EXPECT_EQ(100000 + f.reserved_multi_value_address_space(), after1.dead());
    f._v->update_config(compactAddressSpaceAttributeConfig(true));
    auto old_dead = after1.dead();
    AddressSpace after2 = f.getMultiValueAddressSpaceUsage("after2");
    while (after2.dead() < old_dead) {
        old_dead = after2.dead();
        f._v->commit(); // new commit might trigger further compaction
        after2 = f.getMultiValueAddressSpaceUsage("after2");
    }
    EXPECT_GT(CompactionStrategy::DEAD_ADDRESS_SPACE_SLACK, after2.dead());
}

GTEST_MAIN_RUN_ALL_TESTS()
