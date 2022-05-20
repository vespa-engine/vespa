// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "geo_pos_field_searcher.h"
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/structfieldvalue.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/exception.h>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.searcher.geo_pos_field_searcher");

using search::streaming::QueryTerm;
using search::streaming::QueryTermList;
using search::common::GeoLocation;
using search::common::GeoLocationParser;

namespace vsm {

std::unique_ptr<FieldSearcher> GeoPosFieldSearcher::duplicate() const {
    return std::make_unique<GeoPosFieldSearcher>(*this);
}

GeoPosFieldSearcher::GeoPosFieldSearcher(FieldIdT fId) :
    FieldSearcher(fId),
    _geoPosTerm()
{}

GeoPosFieldSearcher::~GeoPosFieldSearcher() {}

void GeoPosFieldSearcher::prepare(QueryTermList & qtl, const SharedSearcherBuf & buf) {
    _geoPosTerm.clear();
    FieldSearcher::prepare(qtl, buf);
    for (const QueryTerm * qt : qtl) {
        const vespalib::string & str = qt->getTermString();
        GeoLocationParser parser;
        bool valid = parser.parseNoField(str);
        if (! valid) {
            vespalib::Issue::report("invalid position in term: %s", str.c_str());
        }
        _geoPosTerm.emplace_back(parser.getGeoLocation());
    }
}

void GeoPosFieldSearcher::onValue(const document::FieldValue & fv) {
    LOG(spam, "ignore field value '%s'", fv.toString().c_str());
}

void GeoPosFieldSearcher::onStructValue(const document::StructFieldValue & fv) {
    size_t num_terms = _geoPosTerm.size();
    for (size_t j = 0; j < num_terms; ++j) {
        const GeoPosInfo & gpi = _geoPosTerm[j];
        if (gpi.valid() && gpi.cmp(fv)) {
            addHit(*_qtl[j], 0);
        }
    }
    ++_words;
}

bool GeoPosFieldSearcher::GeoPosInfo::cmp(const document::StructFieldValue & sfv) const {
    try {
        auto xv = sfv.getValue("x");
        auto yv = sfv.getValue("y");
        if (xv && yv) {
            int32_t x = xv->getAsInt();
            int32_t y = yv->getAsInt();
            GeoLocation::Point p{x,y};
            if (inside_limit(p)) {
                return true;
            }
        }
    } catch (const vespalib::Exception &e) {
        vespalib::Issue::report("bad fieldvalue for GeoPosFieldSearcher: %s", e.getMessage().c_str());
    }
    return false;
}

}
