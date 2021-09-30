// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/searchsummary/docsummary/getdocsumargs.h>
#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/vespalib/util/jsonwriter.h>

namespace juniper {
    class Config;
    class QueryHandle;
    class Result;
}

namespace search {
class MatchingElements;
class MatchingElementsFields;
}
namespace search::common { class Location; }
namespace search::attribute {
    class IAttributeContext;
    class IAttributeVector;
}
namespace search::docsummary {

class GetDocsumsState;
class IDocsumEnvironment;
class KeywordExtractor;
class DocsumFieldWriterState;

class GetDocsumsStateCallback
{
public:
    virtual void FillSummaryFeatures(GetDocsumsState * state, IDocsumEnvironment * env) = 0;
    virtual void FillRankFeatures(GetDocsumsState * state, IDocsumEnvironment * env) = 0;
    virtual std::unique_ptr<MatchingElements> fill_matching_elements(const MatchingElementsFields &matching_elems_fields) = 0;
    virtual ~GetDocsumsStateCallback(void) { }
    GetDocsumsStateCallback(const GetDocsumsStateCallback &) = delete;
    GetDocsumsStateCallback & operator = (const GetDocsumsStateCallback &) = delete;
protected:
    GetDocsumsStateCallback() = default;
};

/**
 * Per-thread memory shared between all docsum field generators.
 **/
class GetDocsumsState
{
public:
    const search::attribute::IAttributeVector * getAttribute(size_t index) const { return _attributes[index]; }

    GetDocsumArgs               _args;      // from getdocsums request

    uint32_t                   *_docsumbuf; // from getdocsums request
    uint32_t                    _docsumcnt; // from getdocsums request

    KeywordExtractor           *_kwExtractor;
    char                       *_keywords;  // list of keywords from query

    GetDocsumsStateCallback    &_callback;

    struct DynTeaserState {
        uint32_t              _docid;  // document id ('cache key')
        uint32_t              _input;  // input field ('cache key')
        uint32_t              _lang;   // lang field  ('cache key')
        juniper::Config      *_config; // juniper config ('cache key')
        juniper::QueryHandle *_query;  // juniper query representation
        juniper::Result      *_result; // juniper analyze result
    } _dynteaser;


    char                         _docSumFieldSpaceStore[2048];
    search::RawBuf               _docSumFieldSpace;
    std::unique_ptr<search::attribute::IAttributeContext> _attrCtx;
    std::vector<const search::attribute::IAttributeVector *> _attributes;
    std::vector<std::unique_ptr<DocsumFieldWriterState>> _fieldWriterStates;

    // used by AbsDistanceDFW
    std::vector<search::common::GeoLocationSpec> _parsedLocations;
    void parse_locations();

    // used by SummaryFeaturesDFW
    FeatureSet::SP _summaryFeatures;
    bool           _summaryFeaturesCached;
    bool           _omit_summary_features;

    // used by RankFeaturesDFW
    FeatureSet::SP _rankFeatures;

    // Used by AttributeCombinerDFW when filtering is enabled
    std::unique_ptr<search::MatchingElements> _matching_elements;

    GetDocsumsState(const GetDocsumsState &) = delete;
    GetDocsumsState& operator=(const GetDocsumsState &) = delete;
    GetDocsumsState(GetDocsumsStateCallback &callback);
    ~GetDocsumsState();

    const MatchingElements &get_matching_elements(const MatchingElementsFields &matching_elems_fields);
    vespalib::JSONStringer & jsonStringer();
private:
    // Only used by rank/summary features, so make it lazy
    std::unique_ptr<vespalib::JSONStringer>   _jsonStringer;
};

}

