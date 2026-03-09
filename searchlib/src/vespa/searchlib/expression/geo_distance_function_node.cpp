// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geo_distance_function_node.h"
#include "floatresultnode.h"

#include <vespa/searchlib/common/geo_gcd.h>
#include <vespa/vespalib/geo/zcurve.h>

#include <cassert>

using vespalib::Deserializer;
using vespalib::Serializer;
using vespalib::geo::ZCurve;

namespace {

// How many kilometers in a mile obtained from: https://en.wikipedia.org/wiki/Mile
constexpr double one_mile_in_kilometers = 1.609344;
constexpr double miles_per_kilometer = 1.0 / one_mile_in_kilometers;

}

namespace search::expression {

using common::GeoGcd;

IMPLEMENT_EXPRESSIONNODE(GeoDistanceFunctionNode, MultiArgFunctionNode)

GeoDistanceFunctionNode::GeoDistanceFunctionNode(Unit unit) : _unit(unit) {}

GeoDistanceFunctionNode::GeoDistanceFunctionNode() noexcept
    : _unit(Unit::KM) {}

GeoDistanceFunctionNode::~GeoDistanceFunctionNode() = default;

GeoDistanceFunctionNode::GeoDistanceFunctionNode(const GeoDistanceFunctionNode&) = default;

GeoDistanceFunctionNode& GeoDistanceFunctionNode::operator=(const GeoDistanceFunctionNode&) = default;

void GeoDistanceFunctionNode::onPrepareResult() { setResultType(std::make_unique<FloatResultNode>()); }

bool GeoDistanceFunctionNode::onExecute() const {
    assert(getNumArgs() == 3 && "Expect 3 arguments: position attribute, lat, lon");

    for (size_t i = 0; i < 3; i++) {
        if (!getArg(i).execute()) {
            return false;
        }
    }

    int64_t zcurve = getArg(0).getResult()->getInteger();
    int32_t xp, yp;
    ZCurve::decode(zcurve, &xp, &yp);
    double doc_lat = yp / 1.0e6;
    double doc_lng = xp / 1.0e6;

    double query_lat = getArg(1).getResult()->getFloat();
    double query_lng = getArg(2).getResult()->getFloat();

    GeoGcd geo_gcd(query_lat, query_lng);
    double distance = geo_gcd.km_great_circle_distance(doc_lat, doc_lng);

    if (_unit == Unit::MILES) {
        distance *= miles_per_kilometer;
    }

    auto& result = static_cast<FloatResultNode&>(updateResult());
    result.set(distance);

    return true;
}

Serializer& GeoDistanceFunctionNode::onSerialize(Serializer& os) const {
    MultiArgFunctionNode::onSerialize(os);
    uint8_t code = static_cast<uint8_t>(_unit);
    return os << code;
}

Deserializer& GeoDistanceFunctionNode::onDeserialize(Deserializer& is) {
    MultiArgFunctionNode::onDeserialize(is);
    uint8_t code = 0;
    is >> code;
    _unit = static_cast<Unit>(code);
    return is;
}

} // namespace search::expression
