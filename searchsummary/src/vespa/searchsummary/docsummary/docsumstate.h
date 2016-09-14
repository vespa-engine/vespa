// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/juniper/rpinterface.h>

#include <vespa/searchlib/util/rawbuf.h>
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchsummary/docsummary/getdocsumargs.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/searchsummary/docsummary/keywordextractor.h>
#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/vespalib/util/jsonwriter.h>


namespace search {
namespace docsummary {

class GetDocsumsState;

class GetDocsumsStateCallback
{
public:
    virtual void FillSummaryFeatures(GetDocsumsState * state, IDocsumEnvironment * env) = 0;
    virtual void FillRankFeatures(GetDocsumsState * state, IDocsumEnvironment * env) = 0;
    virtual void ParseLocation(GetDocsumsState * state) = 0;
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
private:
    GetDocsumsState(const GetDocsumsState &);
    GetDocsumsState& operator=(const GetDocsumsState &);

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

    search::RawBuf               _docSumFieldSpace;
    char                         _docSumFieldSpaceStore[2048];
    search::attribute::IAttributeContext::UP _attrCtx;
    std::vector<const search::attribute::IAttributeVector *> _attributes;
    vespalib::JSONStringer           _jsonStringer;

    // used by AbsDistanceDFW
    std::unique_ptr<search::common::Location> _parsedLocation;

    // used by SummaryFeaturesDFW
    FeatureSet::SP _summaryFeatures;
    bool           _summaryFeaturesCached;

    // used by RankFeaturesDFW
    FeatureSet::SP _rankFeatures;

    GetDocsumsState(GetDocsumsStateCallback &callback);
    ~GetDocsumsState();
};

}
}

