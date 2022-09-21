// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "getdocsumargs.h"
#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/vespalib/util/stash.h>

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
    virtual void fillSummaryFeatures(GetDocsumsState& state) = 0;
    virtual void fillRankFeatures(GetDocsumsState& state) = 0;
    virtual std::unique_ptr<MatchingElements> fill_matching_elements(const MatchingElementsFields &matching_elems_fields) = 0;
    virtual ~GetDocsumsStateCallback() = default;
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
    std::vector<uint32_t>       _docsumbuf; // from getdocsums request
    KeywordExtractor           *_kwExtractor;

    GetDocsumsStateCallback    &_callback;

    struct DynTeaserState {
        std::unique_ptr<juniper::QueryHandle> _query;  // juniper query representation
    } _dynteaser;


    std::unique_ptr<search::attribute::IAttributeContext> _attrCtx;
    std::vector<const search::attribute::IAttributeVector *> _attributes;
private:
    vespalib::Stash _stash;
public:
    // DocsumFieldWriterState instances are owned by _stash
    std::vector<DocsumFieldWriterState*> _fieldWriterStates;

    // used by AbsDistanceDFW
    std::vector<search::common::GeoLocationSpec> _parsedLocations;
    void parse_locations();

    // used by SummaryFeaturesDFW
    std::shared_ptr<FeatureSet> _summaryFeatures;
    bool           _summaryFeaturesCached;
    bool           _omit_summary_features;

    // used by RankFeaturesDFW
    std::shared_ptr<FeatureSet> _rankFeatures;

    // Used by AttributeCombinerDFW and MultiAttrDFW when filtering is enabled
    std::unique_ptr<search::MatchingElements> _matching_elements;

    GetDocsumsState(const GetDocsumsState &) = delete;
    GetDocsumsState& operator=(const GetDocsumsState &) = delete;
    explicit GetDocsumsState(GetDocsumsStateCallback &callback);
    ~GetDocsumsState();

    const MatchingElements &get_matching_elements(const MatchingElementsFields &matching_elems_fields);
    vespalib::Stash& get_stash() noexcept { return _stash; }
};

}

