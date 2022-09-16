// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "positionsdfw.h"
#include "docsumstate.h"
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/common/geo_gcd.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <cmath>
#include <climits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.positionsdfw");

namespace search::docsummary {

namespace {

double to_degrees(int32_t microDegrees) {
    double d = microDegrees / 1.0e6;
    return d;
}

}

using search::attribute::BasicType;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::IntegerContent;
using search::common::GeoGcd;
using search::common::Location;

LocationAttrDFW::AllLocations
LocationAttrDFW::getAllLocations(GetDocsumsState& state) const
{
    AllLocations retval;
    if (! state._args.locations_possible()) {
        return retval;
    }
    if (state._parsedLocations.empty()) {
        state.parse_locations();
    }
    for (const auto & loc : state._parsedLocations) {
        if (loc.location.valid()) {
            LOG(debug, "found location(field %s) for DFW(field %s)\n",
                loc.field_name.c_str(), getAttributeName().c_str());
            if (getAttributeName() == loc.field_name) {
                retval.matching.push_back(&loc.location);
            } else {
                retval.other.push_back(&loc.location);
            }
        }
    }
    if (retval.empty()) {
        // avoid doing things twice
        state._args.locations_possible(false);
    }
    return retval;
}

LocationAttrDFW::AllLocations::AllLocations() = default;
LocationAttrDFW::AllLocations::~AllLocations() = default;

AbsDistanceDFW::AbsDistanceDFW(const vespalib::string & attrName)
    : LocationAttrDFW(attrName)
{ }

uint64_t
AbsDistanceDFW::findMinDistance(uint32_t docid, GetDocsumsState& state,
                                const std::vector<const GeoLoc *> &locations) const
{
    // ensure result fits in Java "int"
    uint64_t absdist = std::numeric_limits<int32_t>::max();
    uint64_t sqdist = absdist*absdist;
    const auto& attribute = get_attribute(state);
    for (auto location : locations) {
        int32_t docx = 0;
        int32_t docy = 0;
        IntegerContent pos;
        pos.fill(attribute, docid);
        uint32_t numValues = pos.size();
        for (uint32_t i = 0; i < numValues; i++) {
            int64_t docxy(pos[i]);
            vespalib::geo::ZCurve::decode(docxy, &docx, &docy);
            uint64_t dist2 = location->sq_distance_to(GeoLoc::Point{docx, docy});
            if (dist2 < sqdist) {
                sqdist = dist2;
            }
        }
    }
    return (uint64_t) std::sqrt((double) sqdist);
}

void
AbsDistanceDFW::insertField(uint32_t docid, GetDocsumsState& state, vespalib::slime::Inserter &target) const
{
    const auto & all_locations = getAllLocations(state);
    if (all_locations.empty()) {
        return;
    }
    uint64_t absdist = findMinDistance(docid, state, all_locations.best());
    target.insertLong(absdist);
}

//--------------------------------------------------------------------------

PositionsDFW::PositionsDFW(const vespalib::string & attrName, bool useV8geoPositions) :
    AttrDFW(attrName),
    _useV8geoPositions(useV8geoPositions)
{
}

namespace {

void
insertPos(int64_t docxy, vespalib::slime::Inserter &target)
{

    int32_t docx = 0;
    int32_t docy = 0;
    vespalib::geo::ZCurve::decode(docxy, &docx, &docy);
    if (docx == 0 && docy == INT_MIN) {
        LOG(spam, "skipping empty zcurve value");
        return;
    }
    vespalib::slime::Cursor &obj = target.insertObject();
    obj.setLong("y", docy);
    obj.setLong("x", docx);

    double degrees_ns = to_degrees(docy);
    double degrees_ew = to_degrees(docx);

    vespalib::asciistream latlong;
    latlong << vespalib::FloatSpec::fixed;
    if (degrees_ns < 0) {
        latlong << "S" << (-degrees_ns);
    } else {
        latlong << "N" << degrees_ns;
    }
    latlong << ";";
    if (degrees_ew < 0) {
        latlong << "W" << (-degrees_ew);
    } else {
        latlong << "E" << degrees_ew;
    }
    obj.setString("latlong", vespalib::Memory(latlong.str()));
}

void
insertFromAttr(const attribute::IAttributeVector &attribute, uint32_t docid, vespalib::slime::Inserter &target)
{
    IntegerContent pos;
    pos.fill(attribute, docid);
    uint32_t numValues = pos.size();
    LOG(debug, "docid=%d, numValues=%d", docid, numValues);
    if (numValues > 0) {
        if (attribute.getCollectionType() == attribute::CollectionType::SINGLE) {
            insertPos(pos[0], target);
        } else {
            vespalib::slime::Cursor &arr = target.insertArray();
            for (uint32_t i = 0; i < numValues; i++) {
                vespalib::slime::ArrayInserter ai(arr);
                insertPos(pos[i], ai);
            }
        }
    }
}

void insertPosV8(int64_t docxy, vespalib::slime::Inserter &target) {
    int32_t docx = 0;
    int32_t docy = 0;
    vespalib::geo::ZCurve::decode(docxy, &docx, &docy);
    if (docx == 0 && docy == INT_MIN) {
        LOG(spam, "skipping empty zcurve value");
        return;
    }
    double degrees_ns = to_degrees(docy);
    double degrees_ew = to_degrees(docx);
    vespalib::slime::Cursor &obj = target.insertObject();
    obj.setDouble("lat", degrees_ns);
    obj.setDouble("lng", degrees_ew);
    vespalib::asciistream latlong;
    latlong << vespalib::FloatSpec::fixed;
    if (degrees_ns < 0) {
        latlong << "S" << (-degrees_ns);
    } else {
        latlong << "N" << degrees_ns;
    }
    latlong << ";";
    if (degrees_ew < 0) {
        latlong << "W" << (-degrees_ew);
    } else {
        latlong << "E" << degrees_ew;
    }
    obj.setString("latlong", vespalib::Memory(latlong.str()));
}


void insertV8FromAttr(const attribute::IAttributeVector &attribute, uint32_t docid, vespalib::slime::Inserter &target) {
    IntegerContent pos;
    pos.fill(attribute, docid);
    uint32_t numValues = pos.size();
    LOG(debug, "docid=%d, numValues=%d", docid, numValues);
    if (numValues > 0) {
        if (attribute.getCollectionType() == attribute::CollectionType::SINGLE) {
            insertPosV8(pos[0], target);
        } else {
            vespalib::slime::Cursor &arr = target.insertArray();
            for (uint32_t i = 0; i < numValues; i++) {
                vespalib::slime::ArrayInserter ai(arr);
                insertPosV8(pos[i], ai);
            }
        }
    }
}

} // namespace

void
PositionsDFW::insertField(uint32_t docid, GetDocsumsState& dsState, vespalib::slime::Inserter &target) const
{
    if (_useV8geoPositions) {
        insertV8FromAttr(get_attribute(dsState), docid, target);
    } else {
        insertFromAttr(get_attribute(dsState), docid, target);
    }
}

//--------------------------------------------------------------------------

PositionsDFW::UP PositionsDFW::create(const char *attribute_name, const IAttributeManager *attribute_manager, bool useV8geoPositions) {
    if (attribute_manager != nullptr) {
        if (!attribute_name) {
            LOG(debug, "createPositionsDFW: missing attribute name '%p'", attribute_name);
            return {};
        }
        IAttributeContext::UP context = attribute_manager->createContext();
        if (!context.get()) {
            LOG(debug, "createPositionsDFW: could not create context from attribute manager");
            return {};
        }
        const IAttributeVector *attribute = context->getAttribute(attribute_name);
        if (!attribute) {
            LOG(debug, "createPositionsDFW: could not get attribute '%s' from context", attribute_name);
            return {};
        }
    }
    return std::make_unique<PositionsDFW>(attribute_name, useV8geoPositions);
}

std::unique_ptr<DocsumFieldWriter>
AbsDistanceDFW::create(const char *attribute_name, const IAttributeManager *attribute_manager) {
    if (attribute_manager != nullptr) {
        if (!attribute_name) {
            LOG(debug, "createAbsDistanceDFW: missing attribute name '%p'", attribute_name);
            return {};
        }
        IAttributeContext::UP context = attribute_manager->createContext();
        if (!context.get()) {
            LOG(debug, "createAbsDistanceDFW: could not create context from attribute manager");
            return {};
        }
        const IAttributeVector *attribute = context->getAttribute(attribute_name);
        if (!attribute) {
            LOG(debug, "createAbsDistanceDFW: could not get attribute '%s' from context", attribute_name);
            return {};
        }
    }
    return std::make_unique<AbsDistanceDFW>(attribute_name);
}

}
