// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_test");

using namespace config;
using namespace vespa::config::search;
using namespace search;
using namespace search::attribute;
using std::shared_ptr;
using vespalib::stringref;

using BT = BasicType;
using CT = CollectionType;
using AVSP = AttributeVector::SP;

namespace search {

using TestAttributeBase = MultiValueNumericAttribute< IntegerAttributeTemplate<int32_t>, int32_t>;

class TestAttribute : public TestAttributeBase
{
public:
    explicit TestAttribute(const std::string &name)
        : TestAttributeBase(name)
    {}

    generation_t getGen() const { return getCurrentGeneration(); }
    uint32_t getRefCount(generation_t gen) const { return getGenerationRefCount(gen); }
    void incGen() { incGeneration(); }
    generation_t oldest_used_gen() const { return get_oldest_used_generation(); }
};


TEST("Test attribute guards")
{
    auto v = std::make_shared<TestAttribute>("mvint");
    EXPECT_EQUAL(v->getGen(), unsigned(0));
    EXPECT_EQUAL(v->getRefCount(0), unsigned(0));
    EXPECT_EQUAL(v->oldest_used_gen(), unsigned(0));
    {
        AttributeGuard g0(v);
        EXPECT_EQUAL(v->getGen(), unsigned(0));
        EXPECT_EQUAL(v->getRefCount(0), unsigned(1));
        EXPECT_EQUAL(v->oldest_used_gen(), unsigned(0));
        {
            AttributeGuard g1(v);
            EXPECT_EQUAL(v->getGen(), unsigned(0));
            EXPECT_EQUAL(v->getRefCount(0), unsigned(2));
            EXPECT_EQUAL(v->oldest_used_gen(), unsigned(0));
        }
        EXPECT_EQUAL(v->getRefCount(0), unsigned(1));
        EXPECT_EQUAL(v->oldest_used_gen(), unsigned(0));
    }
    EXPECT_EQUAL(v->getRefCount(0), unsigned(0));
    EXPECT_EQUAL(v->oldest_used_gen(), unsigned(0));

    v->incGen();
    EXPECT_EQUAL(v->getGen(), unsigned(1));
    EXPECT_EQUAL(v->getRefCount(0), unsigned(0));
    EXPECT_EQUAL(v->getRefCount(1), unsigned(0));
    EXPECT_EQUAL(v->oldest_used_gen(), unsigned(1));
    {
        AttributeGuard g0(v);
        EXPECT_EQUAL(v->getGen(), unsigned(1));
        EXPECT_EQUAL(v->getRefCount(0), unsigned(0));
        EXPECT_EQUAL(v->getRefCount(1), unsigned(1));
        EXPECT_EQUAL(v->oldest_used_gen(), unsigned(1));
        {
            v->incGen();
            AttributeGuard g1(v);
            EXPECT_EQUAL(v->getGen(), unsigned(2));
            EXPECT_EQUAL(v->getRefCount(0), unsigned(0));
            EXPECT_EQUAL(v->getRefCount(1), unsigned(1));
            EXPECT_EQUAL(v->getRefCount(2), unsigned(1));
            EXPECT_EQUAL(v->oldest_used_gen(), unsigned(1));
        }
        EXPECT_EQUAL(v->getRefCount(0), unsigned(0));
        EXPECT_EQUAL(v->getRefCount(1), unsigned(1));
        EXPECT_EQUAL(v->getRefCount(2), unsigned(0));
        EXPECT_EQUAL(v->oldest_used_gen(), unsigned(1));
    }
    EXPECT_EQUAL(v->getRefCount(0), unsigned(0));
    EXPECT_EQUAL(v->getRefCount(1), unsigned(0));
    EXPECT_EQUAL(v->getRefCount(2), unsigned(0));
    EXPECT_EQUAL(v->oldest_used_gen(), unsigned(1));
    v->update_oldest_used_generation();
    EXPECT_EQUAL(v->oldest_used_gen(), unsigned(2));
    EXPECT_EQUAL(v->getGen(), unsigned(2));
}


void
verifyLoad(AttributeVector & v)
{
    EXPECT_TRUE( !v.isLoaded() );
    EXPECT_TRUE( v.load() );
    EXPECT_TRUE( v.isLoaded() );
    EXPECT_EQUAL( v.getNumDocs(), size_t(100) );
}


TEST("Test loading of attributes")
{
    {
        TestAttributeBase v("mvint");
        EXPECT_TRUE(!v.isLoaded());
        for(size_t i(0); i < 100; i++) {
            AttributeVector::DocId doc;
            EXPECT_TRUE( v.addDoc(doc) );
            EXPECT_TRUE( doc == i);
        }
        EXPECT_TRUE( v.getNumDocs() == 100);
        for(size_t i(0); i < 100; i++) {
            for(size_t j(0); j < i; j++) {
                EXPECT_TRUE( v.append(i, j, 1) );
            }
            v.commit();
            EXPECT_TRUE(size_t(v.getValueCount(i)) == i);
            EXPECT_EQUAL(v.getMaxValueCount(), std::max(size_t(1), i));
        }
        EXPECT_TRUE(v.isLoaded());
        EXPECT_TRUE(v.save());
        EXPECT_TRUE(v.isLoaded());
    }
    {
        TestAttributeBase v("mvint");
        verifyLoad(v);
    }
    {
        Config config(BT::INT32,
                                       CollectionType::ARRAY);
        TestAttributeBase v("mvint", config);
        verifyLoad(v);
    }
    {
        AttributeManager manager;
        Config config(BT::INT32,
                                       CollectionType::ARRAY);
        EXPECT_TRUE(manager.addVector("mvint", config));
        AttributeManager::AttributeList list;
        manager.getAttributeList(list);
        EXPECT_TRUE(list.size() == 1);
        EXPECT_TRUE( list[0]->isLoaded());
        AttributeGuard::UP attrG(manager.getAttribute("mvint"));
        EXPECT_TRUE( attrG->valid() );
    }
}


bool
assertDataType(BT::Type exp, AttributesConfig::Attribute::Datatype in)
{
    AttributesConfig::Attribute a;
    a.datatype = in;
    return EXPECT_EQUAL(exp, ConfigConverter::convert(a).basicType().type());
}


bool
assertCollectionType(CollectionType exp, AttributesConfig::Attribute::Collectiontype in,
                     bool removeIfZ = false, bool createIfNe = false)
{
    AttributesConfig::Attribute a;
    a.collectiontype = in;
    a.removeifzero = removeIfZ;
    a.createifnonexistent = createIfNe;
    Config out = ConfigConverter::convert(a);
    return EXPECT_EQUAL(exp.type(), out.collectionType().type()) &&
        EXPECT_EQUAL(exp.removeIfZero(), out.collectionType().removeIfZero()) &&
        EXPECT_EQUAL(exp.createIfNonExistant(), out.collectionType().createIfNonExistant());
}

void
expect_distance_metric(AttributesConfig::Attribute::Distancemetric in_metric,
                       DistanceMetric out_metric)
{
    AttributesConfig::Attribute a;
    a.distancemetric = in_metric;
    auto out = ConfigConverter::convert(a);
    EXPECT_TRUE(out.distance_metric() == out_metric);
}


TEST("require that config can be converted")
{
    using AVBT = BT;
    using AVCT = CollectionType;
    using CACA = AttributesConfig::Attribute;
    using CACAD = CACA::Datatype;
    using CACAC = CACA::Collectiontype;
    using CC = ConfigConverter;

    EXPECT_TRUE(assertDataType(AVBT::STRING, CACAD::STRING));
    EXPECT_TRUE(assertDataType(AVBT::INT8, CACAD::INT8));
    EXPECT_TRUE(assertDataType(AVBT::INT16, CACAD::INT16));
    EXPECT_TRUE(assertDataType(AVBT::INT32, CACAD::INT32));
    EXPECT_TRUE(assertDataType(AVBT::INT64, CACAD::INT64));
    EXPECT_TRUE(assertDataType(AVBT::FLOAT, CACAD::FLOAT));
    EXPECT_TRUE(assertDataType(AVBT::DOUBLE, CACAD::DOUBLE));
    EXPECT_TRUE(assertDataType(AVBT::PREDICATE, CACAD::PREDICATE));
    EXPECT_TRUE(assertDataType(AVBT::TENSOR, CACAD::TENSOR));
    EXPECT_TRUE(assertDataType(AVBT::REFERENCE, CACAD::REFERENCE));
    EXPECT_TRUE(assertDataType(AVBT::RAW, CACAD::RAW));
    EXPECT_TRUE(assertDataType(AVBT::NONE, CACAD::NONE));

    EXPECT_TRUE(assertCollectionType(AVCT::SINGLE, CACAC::SINGLE));
    EXPECT_TRUE(assertCollectionType(AVCT::ARRAY, CACAC::ARRAY));
    EXPECT_TRUE(assertCollectionType(AVCT::WSET, CACAC::WEIGHTEDSET));
    EXPECT_TRUE(assertCollectionType(AVCT(AVCT::SINGLE, true, false),
                                    CACAC::SINGLE, true, false));
    EXPECT_TRUE(assertCollectionType(AVCT(AVCT::SINGLE, false, true),
                                    CACAC::SINGLE, false, true));

    { // fastsearch
        CACA a;
        EXPECT_TRUE(!CC::convert(a).fastSearch());
        a.fastsearch = true;
        EXPECT_TRUE(CC::convert(a).fastSearch());
    }
    { // fastAccess
        CACA a;
        EXPECT_TRUE(!CC::convert(a).fastAccess());
        a.fastaccess = true;
        EXPECT_TRUE(CC::convert(a).fastAccess());
    }
    {
        CACA a;
        EXPECT_EQUAL(130000u, CC::convert(a).getMaxUnCommittedMemory());
        a.maxuncommittedmemory = 23523;
        EXPECT_EQUAL(23523u, CC::convert(a).getMaxUnCommittedMemory());
    }
    {
        CACA a;
        EXPECT_TRUE(!CC::convert(a).isMutable());
        a.ismutable = true;
        EXPECT_TRUE(CC::convert(a).isMutable());
    }
    {
        CACA a;
        EXPECT_TRUE(!CC::convert(a).paged());
        a.paged = true;
        EXPECT_TRUE(CC::convert(a).paged());
    }
    { // tensor
        CACA a;
        a.datatype = CACAD::TENSOR;
        a.tensortype = "tensor(x[5])";
        Config out = ConfigConverter::convert(a);
        EXPECT_EQUAL("tensor(x[5])", out.tensorType().to_spec());
    }
    { // distance metric (default)
        CACA a;
        auto out = ConfigConverter::convert(a);
        EXPECT_TRUE(out.distance_metric() == DistanceMetric::Euclidean);
    }
    { // distance metric (explicit)
        expect_distance_metric(AttributesConfig::Attribute::Distancemetric::EUCLIDEAN, DistanceMetric::Euclidean);
        expect_distance_metric(AttributesConfig::Attribute::Distancemetric::ANGULAR, DistanceMetric::Angular);
        expect_distance_metric(AttributesConfig::Attribute::Distancemetric::GEODEGREES, DistanceMetric::GeoDegrees);
        expect_distance_metric(AttributesConfig::Attribute::Distancemetric::HAMMING, DistanceMetric::Hamming);
        expect_distance_metric(AttributesConfig::Attribute::Distancemetric::INNERPRODUCT, DistanceMetric::InnerProduct);
        expect_distance_metric(AttributesConfig::Attribute::Distancemetric::PRENORMALIZED_ANGULAR, DistanceMetric::PrenormalizedAngular);
        expect_distance_metric(AttributesConfig::Attribute::Distancemetric::DOTPRODUCT, DistanceMetric::Dotproduct);
    }
    { // hnsw index default params (enabled)
        CACA a;
        a.index.hnsw.enabled = true;
        auto out = ConfigConverter::convert(a);
        EXPECT_TRUE(out.hnsw_index_params().has_value());
        const auto& params = out.hnsw_index_params().value();
        EXPECT_EQUAL(16u, params.max_links_per_node());
        EXPECT_EQUAL(200u, params.neighbors_to_explore_at_insert());
        EXPECT_TRUE(params.multi_threaded_indexing());
    }
    { // hnsw index params (enabled)
        auto dm_in = AttributesConfig::Attribute::Distancemetric::ANGULAR;
        auto dm_out = DistanceMetric::Angular;
        CACA a;
        a.distancemetric = dm_in;
        a.index.hnsw.enabled = true;
        a.index.hnsw.maxlinkspernode = 32;
        a.index.hnsw.neighborstoexploreatinsert = 300;
        a.index.hnsw.multithreadedindexing = false;
        auto out = ConfigConverter::convert(a);
        EXPECT_TRUE(out.hnsw_index_params().has_value());
        const auto& params = out.hnsw_index_params().value();
        EXPECT_EQUAL(32u, params.max_links_per_node());
        EXPECT_EQUAL(300u, params.neighbors_to_explore_at_insert());
        EXPECT_TRUE(params.distance_metric() == dm_out);
        EXPECT_FALSE(params.multi_threaded_indexing());
    }
    { // hnsw index params (disabled)
        CACA a;
        a.index.hnsw.enabled = false;
        auto out = ConfigConverter::convert(a);
        EXPECT_FALSE(out.hnsw_index_params().has_value());
    }
}

bool gt_attribute(const attribute::IAttributeVector * a, const attribute::IAttributeVector * b) {
    return a->getName() < b->getName();
}

TEST("test the attribute context")
{
    std::vector<AVSP> attrs;
    // create various attributes vectors
    attrs.push_back(AttributeFactory::createAttribute("sint32", Config(BT::INT32, CT::SINGLE)));
    attrs.push_back(AttributeFactory::createAttribute("aint32", Config(BT::INT32, CT::ARRAY)));
    attrs.push_back(AttributeFactory::createAttribute("wsint32", Config(BT::INT32, CT::WSET)));
    attrs.push_back(AttributeFactory::createAttribute("dontcare", Config(BT::INT32, CT::SINGLE)));

    // add docs
    for (uint32_t i = 0; i < attrs.size(); ++i) {
        attrs[i]->addDocs(64);
    }

    // commit all attributes (current generation -> 1);
    for (uint32_t i = 0; i < attrs.size(); ++i) {
        attrs[i]->commit();
    }

    AttributeManager manager;
    // add to manager
    for (uint32_t i = 0; i < attrs.size(); ++i) {
        manager.add(attrs[i]);
    }

    {
        IAttributeContext::UP first = manager.createContext();

        // no generation guards taken yet
        for (uint32_t i = 0; i < attrs.size(); ++i) {
            EXPECT_EQUAL(attrs[i]->getCurrentGeneration(), 1u);
            EXPECT_EQUAL(attrs[i]->getGenerationRefCount(1u), 0u);
        }

        for (uint32_t i = 0; i < 2; ++i) {
            EXPECT_TRUE(first->getAttribute("sint32") != nullptr);
            EXPECT_TRUE(first->getAttribute("aint32") != nullptr);
            EXPECT_TRUE(first->getAttribute("wsint32") != nullptr);
            EXPECT_TRUE(first->getAttributeStableEnum("wsint32") != nullptr);
        }
        EXPECT_TRUE(first->getAttribute("foo") == nullptr);
        EXPECT_TRUE(first->getAttribute("bar") == nullptr);

        // one generation guard taken per attribute asked for
        for (uint32_t i = 0; i < attrs.size(); ++i) {
            EXPECT_EQUAL(attrs[i]->getCurrentGeneration(), 1u);
            EXPECT_EQUAL(attrs[i]->getGenerationRefCount(1u),
                       (i < 3) ? (i == 2 ? 2u : 1u) : 0u);
        }

        {
            IAttributeContext::UP second = manager.createContext();

            EXPECT_TRUE(second->getAttribute("sint32") != nullptr);
            EXPECT_TRUE(second->getAttribute("aint32") != nullptr);
            EXPECT_TRUE(second->getAttribute("wsint32") != nullptr);
            EXPECT_TRUE(second->getAttributeStableEnum("wsint32") != nullptr);

            // two generation guards taken per attribute asked for
            for (uint32_t i = 0; i < attrs.size(); ++i) {
                EXPECT_EQUAL(attrs[i]->getCurrentGeneration(), 1u);
                EXPECT_EQUAL(attrs[i]->getGenerationRefCount(1u),
                           (i < 3) ? (i == 2 ? 4u : 2u) : 0u);
            }
        }

        // one generation guard taken per attribute asked for
        for (uint32_t i = 0; i < attrs.size(); ++i) {
            EXPECT_EQUAL(attrs[i]->getCurrentGeneration(), 1u);
            EXPECT_EQUAL(attrs[i]->getGenerationRefCount(1u),
                       (i < 3) ? (i == 2 ? 2u : 1u) : 0u);
        }
    }

    // no generation guards taken
    for (uint32_t i = 0; i < attrs.size(); ++i) {
        EXPECT_EQUAL(attrs[i]->getCurrentGeneration(), 1u);
        EXPECT_EQUAL(attrs[i]->getGenerationRefCount(1u), 0u);
    }

    {
        IAttributeContext::UP ctx = manager.createContext();
        std::vector<const attribute::IAttributeVector *> all;
        ctx->getAttributeList(all);
        EXPECT_EQUAL(4u, all.size());
        std::sort(all.begin(), all.end(), gt_attribute);
        EXPECT_EQUAL("aint32",   all[0]->getName());
        EXPECT_EQUAL("dontcare", all[1]->getName());
        EXPECT_EQUAL("sint32",   all[2]->getName());
        EXPECT_EQUAL("wsint32",  all[3]->getName());
    }
}

TEST("require that we can get readable attribute by name")
{
    auto attr = AttributeFactory::createAttribute("cool_attr", Config(BT::INT32, CT::SINGLE));
    // Ensure there's something to actually load, or fetching the attribute will throw.
    attr->addDocs(64);
    attr->commit();
    AttributeManager manager;
    manager.add(attr);
    auto av = manager.readable_attribute_vector("cool_attr");
    EXPECT_EQUAL(av.get(), static_cast<ReadableAttributeVector*>(attr.get()));
    av = manager.readable_attribute_vector("uncool_attr");
    EXPECT_TRUE(av.get() == nullptr);
}

} // namespace search


TEST_MAIN() { TEST_RUN_ALL(); }
