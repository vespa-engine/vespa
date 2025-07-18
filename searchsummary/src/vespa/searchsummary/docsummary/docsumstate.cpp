// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumstate.h"
#include "docsum_field_writer_state.h"
#include <vespa/juniper/rpinterface.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/juniper/queryhandle.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/issue.h>
#include <cassert>

using search::common::GeoLocationParser;
using search::common::GeoLocationSpec;
using vespalib::FeatureSet;
using vespalib::Issue;

namespace search::docsummary {

GetDocsumsState::DynTeaserState::DynTeaserState()
    : _queries()
{
}

GetDocsumsState::DynTeaserState::~DynTeaserState() = default;

std::unique_ptr<juniper::QueryHandle>&
GetDocsumsState::DynTeaserState::get_query(std::string_view field)
{
    return _queries[std::string(field)];
}

GetDocsumsState::GetDocsumsState(GetDocsumsStateCallback &callback)
    : _args(),
      _docsumbuf(),
      _callback(callback),
      _dynteaser(),
      _attrCtx(),
      _attributes(),
      _stash(),
      _normalization(nullptr),
      _fieldWriterStates(),
      _matching_elements_fields(),
      _parsedLocations(),
      _summaryFeatures(nullptr),
      _omit_summary_features(false),
      _rankFeatures(nullptr),
      _matching_elements(),
      _summary_features_elements(),
      _summary_features_elements_keys()
{
}


GetDocsumsState::~GetDocsumsState() = default;

const MatchingElements &
GetDocsumsState::get_matching_elements()
{
    if (!_matching_elements) {
        _matching_elements = _callback.fill_matching_elements(*_matching_elements_fields);
    }
    return *_matching_elements;
}

const FeatureSet&
GetDocsumsState::get_summary_features()
{
    if (!_summaryFeatures) {
        _callback.fillSummaryFeatures(*this);
        if (!_summaryFeatures) {
            // No summary features have been specified in rank profile
            _summaryFeatures = std::make_shared<FeatureSet>(std::vector<std::string>(), 0);
        }
    }
    return *_summaryFeatures;
}

void
GetDocsumsState::parse_locations()
{
    using document::PositionDataType;
    assert(_parsedLocations.empty()); // only allowed to call this once
    if (! _args.getLocation().empty()) {
        GeoLocationParser parser;
        if (parser.parseWithField(_args.getLocation())) {
            auto view = parser.getFieldName();
            auto attr_name = PositionDataType::getZCurveFieldName(view);
            GeoLocationSpec spec{attr_name, parser.getGeoLocation()};
            _parsedLocations.push_back(spec);
        } else {
            Issue::report("could not parse location string '%s' from request",
                          _args.getLocation().c_str());
        }
    }
    auto stackdump = _args.getStackDump();
    if (! stackdump.empty()) {
        search::SimpleQueryStackDumpIterator iterator(stackdump);
        while (iterator.next()) {
            if (iterator.getType() == search::ParseItem::ITEM_GEO_LOCATION_TERM) {
                std::string view = iterator.index_as_string();
                std::string term(iterator.getTerm());
                GeoLocationParser parser;                
                if (parser.parseNoField(term)) {
                    auto attr_name = PositionDataType::getZCurveFieldName(view);
                    GeoLocationSpec spec{attr_name, parser.getGeoLocation()};
                    _parsedLocations.push_back(spec);
                } else {
                    Issue::report("could not parse location string '%s' from stack dump",
                                  term.c_str());
                }
            }
        }
    }
}


}
