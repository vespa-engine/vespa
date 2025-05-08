// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "getdocsumargs.h"
#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/query/query_normalization.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/featureset.h>
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
class DocsumFieldWriterState;

class GetDocsumsStateCallback
{
public:
    virtual void fillSummaryFeatures(GetDocsumsState& state) = 0;
    virtual void fillRankFeatures(GetDocsumsState& state) = 0;
    virtual void fill_matching_elements(GetDocsumsState& state) = 0;
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
    using FeatureSet = vespalib::FeatureSet;
    const search::attribute::IAttributeVector * getAttribute(size_t index) const { return _attributes[index]; }

    GetDocsumArgs               _args;      // from getdocsums request
    std::vector<uint32_t>       _docsumbuf; // from getdocsums request

    GetDocsumsStateCallback    &_callback;

    class DynTeaserState {
        vespalib::hash_map<std::string, std::unique_ptr<juniper::QueryHandle>> _queries;  // juniper query representations
    public:
        DynTeaserState();
        ~DynTeaserState();
        std::unique_ptr<juniper::QueryHandle>& get_query(std::string_view field);
    };
    DynTeaserState _dynteaser;
    std::unique_ptr<search::attribute::IAttributeContext> _attrCtx;
    std::vector<const search::attribute::IAttributeVector *> _attributes;
private:
    vespalib::Stash           _stash;
    const QueryNormalization *_normalization;
public:
    // DocsumFieldWriterState instances are owned by _stash
    std::vector<DocsumFieldWriterState*> _fieldWriterStates;
    const MatchingElementsFields*        _matching_elements_fields;

    // used by AbsDistanceDFW
    std::vector<search::common::GeoLocationSpec> _parsedLocations;
    void parse_locations();

    // used by SummaryFeaturesDFW
    std::shared_ptr<FeatureSet> _summaryFeatures;
    bool           _omit_summary_features;

    // used by RankFeaturesDFW
    std::shared_ptr<FeatureSet> _rankFeatures;

    // Used by AttributeCombinerDFW and MultiAttrDFW when filtering is enabled
    std::unique_ptr<search::MatchingElements> _matching_elements;

    GetDocsumsState(const GetDocsumsState &) = delete;
    GetDocsumsState& operator=(const GetDocsumsState &) = delete;
    explicit GetDocsumsState(GetDocsumsStateCallback &callback);
    ~GetDocsumsState();

    const MatchingElements &get_matching_elements();
    vespalib::Stash& get_stash() noexcept { return _stash; }
    const QueryNormalization * query_normalization() const { return _normalization; }
    void query_normalization(const QueryNormalization * normalization) { _normalization = normalization; }
};

}

