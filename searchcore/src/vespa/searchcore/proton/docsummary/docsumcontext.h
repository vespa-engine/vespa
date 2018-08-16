// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/docsumstore.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/docsumreply.h>

namespace proton {

/**
 * The DocsumContext class is responsible for performing a docsum request and
 * creating a docsum reply.
 **/
class DocsumContext : public search::docsummary::GetDocsumsStateCallback {
private:
    const search::engine::DocsumRequest  & _request;
    search::docsummary::IDocsumWriter    & _docsumWriter;
    search::docsummary::IDocsumStore     & _docsumStore;
    matching::Matcher::SP                  _matcher;
    matching::ISearchContext             & _searchCtx;
    search::attribute::IAttributeContext & _attrCtx;
    search::IAttributeManager            & _attrMgr;
    const IAttributeExecutor             & _attrExec;
    search::docsummary::GetDocsumsState    _docsumState;
    matching::SessionManager             & _sessionMgr;

    void initState();
    search::engine::DocsumReply::UP createReply();
    std::unique_ptr<vespalib::Slime> createSlimeReply();

public:
    typedef std::unique_ptr<DocsumContext> UP;

    DocsumContext(const search::engine::DocsumRequest & request,
                  search::docsummary::IDocsumWriter & docsumWriter,
                  search::docsummary::IDocsumStore & docsumStore,
                  const matching::Matcher::SP & matcher,
                  matching::ISearchContext & searchCtx,
                  search::attribute::IAttributeContext & attrCtx,
                  search::IAttributeManager & attrMgr,
                  const IAttributeExecutor & attrExec,
                  matching::SessionManager & sessionMgr);

    search::engine::DocsumReply::UP getDocsums();

    // Implements GetDocsumsStateCallback
    void FillSummaryFeatures(search::docsummary::GetDocsumsState * state, search::docsummary::IDocsumEnvironment * env) override;
    void FillRankFeatures(search::docsummary::GetDocsumsState * state, search::docsummary::IDocsumEnvironment * env) override;
    void ParseLocation(search::docsummary::GetDocsumsState * state) override;
};

} // namespace proton

