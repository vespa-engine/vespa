// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "docsumcontext.h"
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchlib/queryeval/begin_and_end_id.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.docsumcontext");

using document::PositionDataType;
using search::common::Location;
using std::string;
using vespalib::slime::SymbolTable;
using vespalib::slime::NIX;
using vespalib::Memory;
using vespalib::slime::Cursor;
using vespalib::slime::Symbol;
using vespalib::slime::Inserter;
using vespalib::slime::ObjectSymbolInserter;
using vespalib::Slime;
using vespalib::make_string;
using namespace search;
using namespace search::attribute;
using namespace search::engine;
using namespace search::docsummary;

namespace proton {

using namespace matching;

namespace {

Memory DOCSUMS("docsums");
Memory DOCSUM("docsum");
Memory ERRORS("errors");
Memory TYPE("type");
Memory MESSAGE("message");
Memory TIMEOUT("timeout");

}

void
DocsumContext::initState()
{
    _docsumState.query_normalization(this);
    const DocsumRequest & req = _request;
    _docsumState._args.initFromDocsumRequest(req);
    auto [session, expectSession] = Matcher::lookupSearchSession(_sessionMgr, req);
    if (session) {
        std::string_view queryStack = session->getStackDump();
        _docsumState._args.setStackDump(queryStack.size(), queryStack.data());
    }
    _docsumState._docsumbuf.clear();
    _docsumState._docsumbuf.reserve(req.hits.size());
    for (const auto & hit : req.hits) {
        _docsumState._docsumbuf.push_back(hit.docid);
    }
}

vespalib::Slime::UP
DocsumContext::createSlimeReply()
{
    IDocsumWriter::ResolveClassInfo rci = _docsumWriter.resolveClassInfo(_docsumState._args.getResultClassName(),
                                                                         _docsumState._args.get_fields());
    _docsumWriter.initState(_attrMgr, _docsumState, rci);
    const size_t estimatedChunkSize(std::min(0x200000ul, _docsumState._docsumbuf.size()*0x400ul));
    auto response = std::make_unique<vespalib::Slime>(Slime::Params(estimatedChunkSize));
    Cursor & root = response->setObject();
    Cursor & array = root.setArray(DOCSUMS);
    const Symbol docsumSym = response->insert(DOCSUM);
    _docsumState._omit_summary_features = (rci.res_class == nullptr) || rci.res_class->omit_summary_features();
    uint32_t num_ok(0);
    for (uint32_t docId : _docsumState._docsumbuf) {
        if (_request.expired() ) { break; }
        Cursor &docSumC = array.addObject();
        ObjectSymbolInserter inserter(docSumC, docsumSym);
        if ((docId != search::endDocId) && rci.res_class != nullptr) {
            _docsumWriter.insertDocsum(rci, docId, _docsumState, _docsumStore, inserter);
        }
        num_ok++;
    }
    if (num_ok != _docsumState._docsumbuf.size()) {
        const uint32_t numTimedOut = _docsumState._docsumbuf.size() - num_ok;
        Cursor & errors = root.setArray(ERRORS);
        Cursor & timeout = errors.addObject();
        timeout.setString(TYPE, TIMEOUT);
        timeout.setString(MESSAGE, make_string("Timed out %d summaries with %" PRId64 "us left.",
                                               numTimedOut, vespalib::count_us(_request.getTimeLeft())));
    }
    return response;
}

DocsumContext::DocsumContext(const DocsumRequest & request, IDocsumWriter & docsumWriter,
                             IDocsumStore & docsumStore, std::shared_ptr<Matcher> matcher,
                             ISearchContext & searchCtx, IAttributeContext & attrCtx,
                             const IAttributeManager & attrMgr, SessionManager & sessionMgr) :
    _request(request),
    _docsumWriter(docsumWriter),
    _docsumStore(docsumStore),
    _matcher(std::move(matcher)),
    _searchCtx(searchCtx),
    _attrCtx(attrCtx),
    _attrMgr(attrMgr),
    _docsumState(*this),
    _sessionMgr(sessionMgr)
{
    initState();
}

DocsumReply::UP
DocsumContext::getDocsums()
{
    return std::make_unique<DocsumReply>(createSlimeReply());
}

void
DocsumContext::fillSummaryFeatures(search::docsummary::GetDocsumsState& state)
{
    assert(&_docsumState == &state);
    if (_matcher->canProduceSummaryFeatures()) {
        state._summaryFeatures = _matcher->getSummaryFeatures(_request, _searchCtx, _attrCtx, _sessionMgr);
    }
}

void
DocsumContext::fillRankFeatures(search::docsummary::GetDocsumsState& state)
{
    assert(&_docsumState == &state);
    // check if we are allowed to run
    if ( ! state._args.dumpFeatures()) {
        return;
    }
    state._rankFeatures = _matcher->getRankFeatures(_request, _searchCtx, _attrCtx, _sessionMgr);
}

void
DocsumContext::fill_matching_elements(search::docsummary::GetDocsumsState& state)
{
    if (_matcher) {
        state._matching_elements = _matcher->get_matching_elements(_request, _searchCtx, _attrCtx, _sessionMgr, *state._matching_elements_fields);
    }
}

bool DocsumContext::is_text_matching(std::string_view) const noexcept {
    // this is for dynamic teaser only; all those fields should be text matching
    return true;
}

Normalizing DocsumContext::normalizing_mode(std::string_view) const noexcept {
    // this is for dynamic teaser only; it always does lowercase/accent removal.
    return Normalizing::LOWERCASE_AND_FOLD;
}

} // namespace proton
