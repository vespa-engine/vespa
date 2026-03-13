// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/common/geo_location.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/common/locationiterators.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cinttypes>

#include <vespa/log/log.h>
LOG_SETUP("location_iterator_test");

using namespace search;
using namespace search::attribute;
using namespace search::common;
using namespace search::fef;

AttributeVector::SP make_attribute(CollectionType collection, bool fast_search = false) {
    Config cfg(BasicType::INT64, collection);
    cfg.setFastSearch(fast_search);
    return AttributeFactory::createAttribute("my_attribute", cfg);
}

void add_docs(AttributeVector::SP attr_ptr, size_t limit = 1000) {
    AttributeVector::DocId docid;
    attr_ptr->addReservedDoc();
    for (size_t i = 1; i < limit; ++i) {
        attr_ptr->addDoc(docid);
    }
    attr_ptr->commit();
    ASSERT_EQ((limit - 1), docid);
}

using Position = std::pair<int32_t, int32_t>;
using Positions = std::vector<Position>;

constexpr double pi = 3.14159265358979323846;
// microdegrees -> degrees -> radians -> km (using Earth mean radius)
constexpr double udeg_to_km = 1.0e-6 * (pi / 180.0) * 6371.0088;

class SingleIteratorTest : public ::testing::Test {
private:
    AttributeVector::SP   _attr;
    IntegerAttribute *    _api;
    TermFieldMatchData    _tfmd;
    std::vector<Position> _positions;

    void set_doc(IntegerAttribute *ia, uint32_t docid, const Position &p) {
	ia->clearDoc(docid);
	int64_t value = vespalib::geo::ZCurve::encode(p.first, p.second);
	LOG(debug, "single: value for docid %u is %" PRId64, docid, value);
	ia->update(docid, value);
	ia->commit();
        if (docid >= _positions.size()) {
            _positions.resize(1+docid);
        }
        _positions[docid] = p;
    }

    void populate_single(IntegerAttribute *ia) {
	Position invalid(0, 0x80000000);
	set_doc(ia, 1, Position(10000, 15000));
	set_doc(ia, 3, invalid);
	set_doc(ia, 5, Position(20000, -25000));
	set_doc(ia, 7, Position(-30000, 35000));
    }

public:
    SingleIteratorTest()
      : _attr(make_attribute(CollectionType::SINGLE, true)),
        _api(dynamic_cast<IntegerAttribute *>(_attr.get()))
    {
        EXPECT_TRUE(_api != nullptr);
        add_docs(_attr);
        populate_single(_api);
    }

    void expect_hits(GeoLocation geo, std::vector<uint32_t> hit_vector) {
        Location bridge(geo);
        bridge.setVec(*_attr);
        auto iterator = create_location_iterator(_tfmd, 9, true, bridge);
        iterator->initFullRange();
        auto hits = hit_vector.cbegin();
        for (uint32_t d = 1; d < 0x80000000; ++d) {
            iterator->seek(d);
            if (iterator->isAtEnd()) {
                break;
            }
            EXPECT_EQ(iterator->getDocId(), *hits++);
            d = std::max(d, iterator->getDocId());
            _tfmd.setRawScore(0, 0.0);
            iterator->unpack(d);
            EXPECT_TRUE(_tfmd.has_ranking_data(d));
            EXPECT_NE(_tfmd.getRawScore(), 0.0);
            int32_t dx = geo.point.x - _positions[d].first;
            int32_t dy = geo.point.y - _positions[d].second;
            double sq_distance = dx*dx + dy*dy;
            double dist = std::sqrt(sq_distance);
            double score = 1.0 / (1.0 + (udeg_to_km * dist));
            LOG(info, "distance[%u] = %.2f, rawscore = %.6f / expected %.6f",
                d, dist, _tfmd.getRawScore(), score);
            EXPECT_DOUBLE_EQ(_tfmd.getRawScore(), score);
        }
        EXPECT_EQ(hits, hit_vector.cend());
        EXPECT_TRUE(iterator->isAtEnd());
    }
};


TEST_F(SingleIteratorTest, finds_locations_sets_rawscore) {
    GeoLocation origin({0, 0}, 1u<<30);
    expect_hits(origin, {1, 5, 7});

    GeoLocation exact({20000, -25000}, 0);
    expect_hits(exact, {5});

    GeoLocation close({-30300, 35400}, 2000);
    expect_hits(close, {7});
}

GTEST_MAIN_RUN_ALL_TESTS()
