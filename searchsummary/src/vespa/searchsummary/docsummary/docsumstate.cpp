// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumstate.h"
#include <vespa/juniper/rpinterface.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/geo_location.h>
#include <vespa/searchlib/common/geo_location_parser.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchlib/parsequery/parse.h>
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include "docsum_field_writer_state.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchsummary.docsummary.docsumstate");

using search::common::GeoLocationParser;
using search::common::GeoLocationSpec;

namespace search::docsummary {

GetDocsumsState::GetDocsumsState(GetDocsumsStateCallback &callback)
    : _args(),
      _docsumbuf(nullptr),
      _docsumcnt(0),
      _kwExtractor(nullptr),
      _keywords(nullptr),
      _callback(callback),
      _dynteaser(),
      _docSumFieldSpaceStore(),
      _docSumFieldSpace(_docSumFieldSpaceStore, sizeof(_docSumFieldSpaceStore)), // only alloc buffer if needed
      _attrCtx(),
      _attributes(),
      _fieldWriterStates(),
      _parsedLocations(),
      _summaryFeatures(nullptr),
      _summaryFeaturesCached(false),
      _omit_summary_features(false),
      _rankFeatures(nullptr),
      _matching_elements(),
      _jsonStringer()
{
    _dynteaser._docid    = static_cast<uint32_t>(-1);
    _dynteaser._input    = static_cast<uint32_t>(-1);
    _dynteaser._lang     = static_cast<uint32_t>(-1);
    _dynteaser._config   = nullptr;
    _dynteaser._query    = nullptr;
    _dynteaser._result   = nullptr;
}


GetDocsumsState::~GetDocsumsState()
{
    free(_docsumbuf);
    free(_keywords);
    if (_dynteaser._result != nullptr) {
        juniper::ReleaseResult(_dynteaser._result);
    }
    if (_dynteaser._query != nullptr) {
        juniper::ReleaseQueryHandle(_dynteaser._query);
    }
}

const MatchingElements &
GetDocsumsState::get_matching_elements(const MatchingElementsFields &matching_elems_fields)
{
    if (!_matching_elements) {
        _matching_elements = _callback.fill_matching_elements(matching_elems_fields);
    }
    return *_matching_elements;
}

vespalib::JSONStringer &
GetDocsumsState::jsonStringer() {
    if (!_jsonStringer) {
        _jsonStringer = std::make_unique<vespalib::JSONStringer>();
    }
    return *_jsonStringer;
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
            LOG(warning, "could not parse location string '%s' from request",
                _args.getLocation().c_str());
        }
    }
    auto stackdump = _args.getStackDump();
    if (! stackdump.empty()) {
        search::SimpleQueryStackDumpIterator iterator(stackdump);
        while (iterator.next()) {
            if (iterator.getType() == search::ParseItem::ITEM_GEO_LOCATION_TERM) {
                vespalib::string view = iterator.getIndexName();
                vespalib::string term = iterator.getTerm();
                GeoLocationParser parser;                
                if (parser.parseNoField(term)) {
                    auto attr_name = PositionDataType::getZCurveFieldName(view);
                    GeoLocationSpec spec{attr_name, parser.getGeoLocation()};
                    _parsedLocations.push_back(spec);
                } else {
                    LOG(warning, "could not parse location string '%s' from stack dump",
                        term.c_str());
                }
            }
        }
    }
}


}
