// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryenvironment.h"
#include <vespa/searchlib/common/geo_location.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/common/geo_location_parser.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.queryenvironment");

using search::IAttributeManager;
using search::common::GeoLocationParser;
using search::common::GeoLocationSpec;
using search::fef::Properties;
using vespalib::string;

namespace streaming {

namespace {

std::vector<GeoLocationSpec>
parseLocation(const string & location_str)
{
    std::vector<GeoLocationSpec> fefLocations;
    if (location_str.empty()) {
        return fefLocations;
    }
    GeoLocationParser locationParser;
    if (!locationParser.parseWithField(location_str)) {
        LOG(warning, "Location parse error (location: '%s'): %s. Location ignored.",
                     location_str.c_str(), locationParser.getParseError());
        return fefLocations;
    }
    auto loc = locationParser.getGeoLocation();
    if (loc.has_point) {
        fefLocations.push_back(GeoLocationSpec{locationParser.getFieldName(), loc});
    }
    return fefLocations;
}

}

QueryEnvironment::QueryEnvironment(const string & location_str,
                                   const IndexEnvironment & indexEnv,
                                   const Properties & properties,
                                   const IAttributeManager * attrMgr) :
    _indexEnv(indexEnv),
    _properties(properties),
    _attrCtx(std::make_unique<AttributeAccessRecorder>(attrMgr->createContext())),
    _queryTerms(),
    _locations(parseLocation(location_str))
{
}

QueryEnvironment::~QueryEnvironment() {}

void QueryEnvironment::addGeoLocation(const vespalib::string &field, const vespalib::string &location_str) {
    GeoLocationParser locationParser;
    if (! locationParser.parseNoField(location_str)) {
        LOG(warning, "Location parse error (location: '%s'): %s. Location ignored.",
                     location_str.c_str(), locationParser.getParseError());
        return;
    }
    auto loc = locationParser.getGeoLocation();
    if (loc.has_point) {
        _locations.push_back(GeoLocationSpec{field, loc});
    }
}

QueryEnvironment::GeoLocationSpecPtrs
QueryEnvironment::getAllLocations() const
{
    GeoLocationSpecPtrs retval;
    for (const auto & loc : _locations) {
        retval.push_back(&loc);
    }
    return retval;
}

} // namespace streaming

