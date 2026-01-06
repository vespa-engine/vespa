// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vespa/searchcommon/attribute/config.h"
#include "vespa/searchlib/attribute/attribute_blueprint_factory.h"
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include "vespa/searchlib/attribute/integerbase.h"
#include <vespa/searchlib/common/geo_location.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/fef/matchdatalayout.h>

#include "vespa/searchlib/query/tree/querybuilder.h"
#include "vespa/searchlib/query/tree/simplequery.h"
#include <vespa/searchlib/query/tree/termnodes.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/global_filter.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/lazy_filter.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vector>

using namespace search;
using namespace search::attribute;
using namespace search::common;
using namespace search::queryeval;

class LazyFilterTest: public ::testing::Test {
protected:
    std::shared_ptr<GlobalFilter> _multiples_of_four;
    std::shared_ptr<GlobalFilter> _multiples_of_six;
    std::shared_ptr<GlobalFilter> _multiples_of_seven;
    std::shared_ptr<GlobalFilter> _multiples_of_eight;

    LazyFilterTest();
    ~LazyFilterTest() override;
    static std::vector<uint32_t> get_multiples_of(uint32_t number, uint32_t up_to);
};

LazyFilterTest::LazyFilterTest()
    : ::testing::Test()
{
    _multiples_of_four = GlobalFilter::create(get_multiples_of(4, 100), 100);
    _multiples_of_six = GlobalFilter::create(get_multiples_of(6, 150), 150);
    _multiples_of_seven = GlobalFilter::create(get_multiples_of(7, 200), 200);
    _multiples_of_eight = GlobalFilter::create(get_multiples_of(8, 200), 200);
}

LazyFilterTest::~LazyFilterTest() = default;

std::vector<uint32_t>
LazyFilterTest::get_multiples_of(uint32_t number, uint32_t up_to) {
    std::vector<uint32_t> multiples;
    for (uint32_t i = 1; number * i < up_to; i += 1) {
        multiples.push_back(number * i);
    }

    return multiples;
}

/**
 * Tests for FallbackFilter
 **/

TEST_F(LazyFilterTest, fallback_filter_is_active) {
    auto and_filter = FallbackFilter::create(*_multiples_of_four, *_multiples_of_eight);

    EXPECT_TRUE(and_filter->is_active());
}

TEST_F(LazyFilterTest, fallback_filter_size) {
    auto and_filter = FallbackFilter::create(*_multiples_of_four, *_multiples_of_eight);

    EXPECT_EQ(100, and_filter->size());
}

TEST_F(LazyFilterTest, fallback_filter_count) {
    auto and_filter = FallbackFilter::create(*_multiples_of_four, *_multiples_of_eight);

    EXPECT_EQ(24, and_filter->count()); // The exact answer would be lower, but we are only getting an upper bound
}

TEST_F(LazyFilterTest, fallback_filter_check) {
    auto and_filter = FallbackFilter::create(*_multiples_of_four, *_multiples_of_eight);

    EXPECT_TRUE(and_filter->check(16));
    EXPECT_FALSE(and_filter->check(12));
    EXPECT_FALSE(and_filter->check(10));
}

class LoggingGlobalFilter : public GlobalFilter {
private:
    const GlobalFilter & _global_filter;
    mutable uint32_t _number_of_checks;

public:
    LoggingGlobalFilter(const GlobalFilter & global_filter) noexcept
        : _global_filter(global_filter),
          _number_of_checks(0) {
    }
    bool is_active() const override { return _global_filter.is_active(); }
    uint32_t size() const override { return _global_filter.size(); }
    uint32_t count() const override { return _global_filter.count(); }
    bool check(uint32_t index) const override { ++_number_of_checks; return _global_filter.check(index); }

    uint32_t get_number_of_checks() const { return _number_of_checks; }
};

TEST_F(LazyFilterTest, fallback_filter_fallback_is_checked_only_when_necessary) {
    std::shared_ptr<LoggingGlobalFilter> logging_multiples_of_four = std::make_shared<LoggingGlobalFilter>(*_multiples_of_four);
    std::shared_ptr<LoggingGlobalFilter> logging_multiples_of_eight = std::make_shared<LoggingGlobalFilter>(*_multiples_of_eight);
    auto and_filter = FallbackFilter::create(*logging_multiples_of_four, *logging_multiples_of_eight);

    EXPECT_EQ(0, logging_multiples_of_four->get_number_of_checks());
    EXPECT_EQ(0, logging_multiples_of_eight->get_number_of_checks());

    EXPECT_FALSE(and_filter->check(10));
    EXPECT_EQ(1, logging_multiples_of_four->get_number_of_checks());
    EXPECT_EQ(0, logging_multiples_of_eight->get_number_of_checks());

    EXPECT_FALSE(and_filter->check(4));
    EXPECT_EQ(2, logging_multiples_of_four->get_number_of_checks());
    EXPECT_EQ(1, logging_multiples_of_eight->get_number_of_checks());

    EXPECT_TRUE(and_filter->check(8));
    EXPECT_EQ(3, logging_multiples_of_four->get_number_of_checks());
    EXPECT_EQ(2, logging_multiples_of_eight->get_number_of_checks());
}

/**
 * Tests for AndFilter
 **/

TEST_F(LazyFilterTest, and_filter_is_active) {
    auto and_filter = AndFilter::create({_multiples_of_four, _multiples_of_six, _multiples_of_seven});

    EXPECT_TRUE(and_filter->is_active());
}

TEST_F(LazyFilterTest, and_filter_size_is_min) {
    auto and_filter = AndFilter::create({_multiples_of_four, _multiples_of_six, _multiples_of_seven});

    EXPECT_EQ(100, and_filter->size());
}

TEST_F(LazyFilterTest, and_filter_count) {
    auto and_filter = AndFilter::create({_multiples_of_four, _multiples_of_six, _multiples_of_seven});

    EXPECT_EQ(24, and_filter->count()); // The exact answer would be 1, but we are only getting an upper bound
}

TEST_F(LazyFilterTest, and_filter_check) {
    auto and_filter = AndFilter::create({_multiples_of_four, _multiples_of_six, _multiples_of_seven});

    EXPECT_FALSE(and_filter->check(24));
    EXPECT_TRUE(and_filter->check(84));
}

/**
 * Tests for LocationLazyFilter
 **/

using Position = std::pair<int32_t, int32_t>;
using Positions = std::vector<Position>;

class LocationLazyFilterTest: public ::testing::Test {
protected:
    search::attribute::test::MockAttributeManager _attribute_manager;
    FieldSpec _field_spec_my_location;
    FieldSpec _field_spec_my_location_many;
    AttributeVector::SP   _location_attribute;
    AttributeVector::SP   _location_attribute_many;

public:
    LocationLazyFilterTest()
        : _attribute_manager(),
          _field_spec_my_location("my_location", 1, 1),
          _field_spec_my_location_many("my_location_many", 2, 2),
          _location_attribute(create_location_attribute(_field_spec_my_location.getName(), 10)),
          _location_attribute_many(create_location_attribute(_field_spec_my_location_many.getName(), 1000)) {
    }
    ~LocationLazyFilterTest() override;

    AttributeVector::SP create_location_attribute(const std::string &name, uint32_t num_documents) {
        // Create AttributeVector
        Config cfg(BasicType::INT64, CollectionType::SINGLE);
        cfg.setFastSearch(true);
        AttributeVector::SP attr = AttributeFactory::createAttribute(name, cfg);
        _attribute_manager.addAttribute(attr);

        // Add documents
        AttributeVector::DocId docid;
        //attr->addReservedDoc(); // called in MockAttributeManager
        for (size_t i = 1; i <= num_documents; ++i) {
            attr->addDoc(docid);
        }
        attr->commit();
        EXPECT_EQ(num_documents, docid);

        // Add positions to documents
        IntegerAttribute *ia = dynamic_cast<IntegerAttribute *>(attr.get());
        EXPECT_TRUE(ia != nullptr);
        Position invalid(0, 0x80000000);
        set_doc(ia, 1, num_documents, Position(10000, 15000));
        set_doc(ia, 3, num_documents, invalid);
        set_doc(ia, 5, num_documents, Position(20000, -25000));
        set_doc(ia, 7, num_documents, Position(-30000, 35000));

        return attr;
    }

    void set_doc(IntegerAttribute *ia, uint32_t docid, uint32_t num_documents, const Position &p) {
        if (docid <= num_documents) {
            ia->clearDoc(docid);
            int64_t value = vespalib::geo::ZCurve::encode(p.first, p.second);
            ia->update(docid, value);
            ia->commit();
        }
    }

    std::shared_ptr<LocationLazyFilter> create_lazy_filter(const GeoLocation &geo_location, uint32_t est_hits = 2, bool empty = false) const {
        Location location(geo_location);
        location.setVec(*_location_attribute);

        Blueprint::HitEstimate estimate(est_hits, empty);
        return LocationLazyFilter::create(location, estimate);
    }
};
LocationLazyFilterTest::~LocationLazyFilterTest() = default; // Defined here to avoid warning

TEST_F(LocationLazyFilterTest, location_filter_is_active) {
    std::shared_ptr<LocationLazyFilter> filter = create_lazy_filter(GeoLocation({0, 0}, 1u << 30));

    EXPECT_TRUE(filter->is_active());
}

TEST_F(LocationLazyFilterTest, location_filter_size) {
    std::shared_ptr<LocationLazyFilter> filter = create_lazy_filter(GeoLocation({0, 0}, 1u << 30));

    // Returns the size of the attribute vector (docid limit minus one)
    EXPECT_EQ(10, filter->size());
}

TEST_F(LocationLazyFilterTest, location_filter_count) {
    std::shared_ptr<LocationLazyFilter> filter = create_lazy_filter(GeoLocation({0, 0}, 1u << 30), 2, false);
    EXPECT_EQ(2, filter->count()); // Returns the estimate given to the filter

    filter = create_lazy_filter(GeoLocation({0, 0}, 1u << 30), 10000, false);
    EXPECT_EQ(10, filter->count()); // Returns size since the estimate is too large

    filter = create_lazy_filter(GeoLocation({0, 0}, 1u << 30), 0, false);
    EXPECT_EQ(0, filter->count());

    filter = create_lazy_filter(GeoLocation({0, 0}, 1u << 30), 0, true);
    EXPECT_EQ(0, filter->count());
}

TEST_F(LocationLazyFilterTest, location_filter_check_origin) {
    std::shared_ptr<LocationLazyFilter> filter = create_lazy_filter(GeoLocation({0, 0}, 1u << 30));

    EXPECT_TRUE(filter->check(1));
    EXPECT_TRUE(filter->check(5));
    EXPECT_TRUE(filter->check(7));

    EXPECT_FALSE(filter->check(2));
    EXPECT_FALSE(filter->check(3));
    EXPECT_FALSE(filter->check(4));
    EXPECT_FALSE(filter->check(6));
    EXPECT_FALSE(filter->check(8));
    EXPECT_FALSE(filter->check(9));
}

TEST_F(LocationLazyFilterTest, location_filter_check_exact_location) {
    std::shared_ptr<LocationLazyFilter> filter = create_lazy_filter(GeoLocation({20000, -25000}, 0));

    EXPECT_FALSE(filter->check(1));
    EXPECT_FALSE(filter->check(3));
    EXPECT_TRUE(filter->check(5));
    EXPECT_FALSE(filter->check(7));
}

TEST_F(LocationLazyFilterTest, location_filter_check_approx_location) {
    std::shared_ptr<LocationLazyFilter> filter = create_lazy_filter(GeoLocation({-30300, 35400}, 2000));

    EXPECT_FALSE(filter->check(1));
    EXPECT_FALSE(filter->check(3));
    EXPECT_FALSE(filter->check(5));
    EXPECT_TRUE(filter->check(7));
}

TEST_F(LocationLazyFilterTest, location_filter_check_docids_over_limit) {
    std::shared_ptr<LocationLazyFilter> filter = create_lazy_filter(GeoLocation({0, 0}, 1u << 30));

    EXPECT_FALSE(filter->check(10));
    EXPECT_FALSE(filter->check(100));
    EXPECT_FALSE(filter->check(1000));
    EXPECT_FALSE(filter->check(10000));
}

/**
 * Tests for filter creation from blueprint
 **/

class LazyFilterCreationTest : public LocationLazyFilterTest {
protected:
    search::AttributeBlueprintFactory _factory;
    IAttributeContext::UP _attribute_context;
    FakeRequestContext _request_context;
    FieldSpec _field_spec_my_double;
    search::fef::MatchDataLayout _mdl;
public:
    LazyFilterCreationTest()
        : _factory(),
          _attribute_context(_attribute_manager.createContext()),
          _request_context(_attribute_context.get()),
          _field_spec_my_double("my_double", 3, 3),
          _mdl() {
        _attribute_manager.addAttribute(search::AttributeFactory::createAttribute(_field_spec_my_double.getName(), Config(BasicType::DOUBLE)));
    }
    Blueprint::UP create_location_blueprint(const FieldSpec& field_spec, const GeoLocation& geo_location) {
            Location location(geo_location);
            //location.setVec(*_location_attribute_many);
            location.setVec(*_attribute_manager.getAttribute(field_spec.getName())->get());

            query::QueryBuilder<query::SimpleQueryNodeTypes> builder;
            builder.addLocationTerm(location, field_spec.getName(), 42, search::query::Weight(1));
            query::Node::UP node = builder.build();

            return _factory.createBlueprint(_request_context, field_spec, *node, _mdl);
    }
    Blueprint::UP create_range_blueprint(const FieldSpec& field_spec, const std::string& from, const std::string& to) {
        search::query::SimpleNumberTerm term(vespalib::make_string("[%s;%s]", from.c_str(), to.c_str()), field_spec.getName(), 0, search::query::Weight(1));
        return _factory.createBlueprint(_request_context, field_spec, term, _mdl);
    }
};

TEST_F(LazyFilterCreationTest, creation_from_location_blueprint) {
    Blueprint::UP root = create_location_blueprint(_field_spec_my_location, GeoLocation({0, 0}, 1u << 30));
    std::cout << root->asString() << std::endl;
    std::shared_ptr<GlobalFilter> filter = root->create_lazy_filter();
    EXPECT_TRUE(filter->is_active());
}

TEST_F(LazyFilterCreationTest, creation_from_location_blueprint_low_hit_ratio) {
    // Having a low hit ratio (less than 10%) means that a LocationPreFilterBlueprint is generated in addition to a LocationPostFilterBlueprint
    // Make sure that we also get a lazy filter in this case
    Blueprint::UP root = create_location_blueprint(_field_spec_my_location_many, GeoLocation({0, 0}, 1u << 30));
    std::cout << root->asString() << std::endl;
    std::shared_ptr<GlobalFilter> filter = root->create_lazy_filter();
    EXPECT_TRUE(filter->is_active());
}

TEST_F(LazyFilterCreationTest, creation_from_and_blueprint) {
    Blueprint::UP bp1 = create_location_blueprint(_field_spec_my_location, GeoLocation({0, 0}, 1u << 30));
    Blueprint::UP bp2 = create_location_blueprint(_field_spec_my_location, GeoLocation({-30300, 35400}, 2000));

    auto root = std::make_unique<AndBlueprint>();
    root->addChild(std::move(bp1));
    root->addChild(std::move(bp2));

    std::shared_ptr<GlobalFilter> filter = root->create_lazy_filter();
    EXPECT_TRUE(filter->is_active());
}

TEST_F(LazyFilterCreationTest, creation_from_larger_and_blueprint) {
    Blueprint::UP bp1 = create_location_blueprint(_field_spec_my_location, GeoLocation({0, 0}, 1u << 30));
    Blueprint::UP bp2 = create_range_blueprint(_field_spec_my_double, "0.0", "1.0");
    Blueprint::UP bp3 = create_location_blueprint(_field_spec_my_location, GeoLocation({-30300, 35400}, 2000));

    auto root = std::make_unique<AndBlueprint>();
    root->addChild(std::move(bp1));
    root->addChild(std::move(bp2));
    root->addChild(std::move(bp3));

    std::shared_ptr<GlobalFilter> filter = root->create_lazy_filter();
    EXPECT_TRUE(filter->is_active());
}

TEST_F(LazyFilterCreationTest, no_creation_from_or_blueprint) {
    Blueprint::UP bp1 = create_location_blueprint(_field_spec_my_location, GeoLocation({0, 0}, 1u << 30));
    Blueprint::UP bp2 = create_location_blueprint(_field_spec_my_location, GeoLocation({-30300, 35400}, 2000));

    auto root = std::make_unique<OrBlueprint>();
    root->addChild(std::move(bp1));
    root->addChild(std::move(bp2));

    std::shared_ptr<GlobalFilter> filter = root->create_lazy_filter();
    EXPECT_FALSE(filter->is_active());
}

GTEST_MAIN_RUN_ALL_TESTS()
