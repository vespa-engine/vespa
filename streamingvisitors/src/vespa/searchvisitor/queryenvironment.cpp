// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryenvironment.h"
#include <vespa/searchlib/common/geo_location_spec.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.queryenvironment");

using search::IAttributeManager;
using search::fef::Properties;
using vespalib::string;

namespace streaming {

namespace {

search::fef::Location
parseLocation(const string & location_str)
{
    search::fef::Location fefLocation;
    if (location_str.empty()) {
        return fefLocation;
    }
    string::size_type pos = location_str.find(':');
    if (pos == string::npos) {
        LOG(warning, "Location string lacks attribute vector specification. loc='%s'. Location ignored.",
                     location_str.c_str());
        return fefLocation;
    }
    string attr = location_str.substr(0, pos);
    const string location = location_str.substr(pos + 1);

    search::common::GeoLocationSpec locationSpec;
    if (!locationSpec.parseOldFormat(location)) {
        LOG(warning, "Location parse error (location: '%s'): %s. Location ignored.",
                     location.c_str(), locationSpec.getParseError());
        return fefLocation;
    }
    fefLocation.setAttribute(attr);
    fefLocation.setXPosition(locationSpec.getX());
    fefLocation.setYPosition(locationSpec.getY());
    fefLocation.setXAspect(locationSpec.getXAspect());
    fefLocation.setValid(true);
    return fefLocation;
}

}

QueryEnvironment::QueryEnvironment(const string & location_str,
                                   const IndexEnvironment & indexEnv,
                                   const Properties & properties,
                                   const IAttributeManager * attrMgr) :
    _indexEnv(indexEnv),
    _properties(properties),
    _attrCtx(attrMgr->createContext()),
    _queryTerms(),
    _location(parseLocation(location_str))
{
}

QueryEnvironment::~QueryEnvironment() {}

} // namespace streaming

