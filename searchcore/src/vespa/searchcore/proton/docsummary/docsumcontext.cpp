// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "docsumcontext.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchlib/queryeval/begin_and_end_id.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/common/transport.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.docsumcontext");

using document::PositionDataType;
using search::common::Location;
using vespalib::string;
using vespalib::slime::SymbolTable;
using vespalib::slime::NIX;
using vespalib::Memory;
using vespalib::slime::Cursor;
using vespalib::slime::Symbol;
using vespalib::slime::Inserter;
using vespalib::slime::ObjectSymbolInserter;
using vespalib::Slime;
using namespace search;
using namespace search::attribute;
using namespace search::engine;
using namespace search::docsummary;

namespace proton {

using namespace matching;

namespace {

Memory DOCSUMS("docsums");
Memory DOCSUM("docsum");

void addTimedOut(Inserter & inserter, fastos::TimeStamp left) {
    inserter.insertObject().setString("error", vespalib::make_string("Timeout at %ldus", left.us()));
}

}

void
DocsumContext::initState()
{
    const DocsumRequest & req = _request;
    _docsumState._args.initFromDocsumRequest(req);
    _docsumState._args.SetQueryFlags(req.queryFlags & ~search::fs4transport::QFLAG_DROP_SORTDATA);
    _docsumState._docsumcnt = req.hits.size();

    if (_docsumState._docsumcnt > 0) {
        _docsumState._docsumbuf = (uint32_t*)malloc(sizeof(uint32_t) * _docsumState._docsumcnt);
    } else {
        _docsumState._docsumbuf = NULL;
    }
    for (uint32_t i = 0; i < _docsumState._docsumcnt; i++) {
        _docsumState._docsumbuf[i] = req.hits[i].docid;
    }
}

DocsumReply::UP
DocsumContext::createReply()
{
    DocsumReply::UP reply(new DocsumReply());
    search::RawBuf buf(4096);
    _docsumWriter.InitState(_attrMgr, &_docsumState);
    reply->docsums.resize(_docsumState._docsumcnt);
    SymbolTable::UP symbols = std::make_unique<SymbolTable>();
    IDocsumWriter::ResolveClassInfo rci = _docsumWriter.resolveClassInfo(_docsumState._args.getResultClassName(), _docsumStore.getSummaryClassId());
    for (uint32_t i = 0; i < _docsumState._docsumcnt; ++i) {
        buf.reset();
        uint32_t docId = _docsumState._docsumbuf[i];
        reply->docsums[i].docid = docId;
        if (docId != search::endDocId && !rci.mustSkip) {
            Slime slime(Slime::Params(std::move(symbols)));
            vespalib::slime::SlimeInserter inserter(slime);
            if (_request.expired()) {
                addTimedOut(inserter, _request.getTimeLeft());
            } else {
                _docsumWriter.insertDocsum(rci, docId, &_docsumState, &_docsumStore, slime, inserter);
            }
            uint32_t docsumLen = (slime.get().type().getId() != NIX::ID)
                                   ? IDocsumWriter::slime2RawBuf(slime, buf)
                                   : 0;
            reply->docsums[i].setData(buf.GetDrainPos(), docsumLen);
            symbols = Slime::reclaimSymbols(std::move(slime));
        }
    }
    return reply;
}

namespace {

vespalib::Slime::Params
makeSlimeParams(size_t chunkSize) {
    Slime::Params params;
    params.setChunkSize(chunkSize);
    return params;
}

}

vespalib::Slime::UP
DocsumContext::createSlimeReply()
{
    _docsumWriter.InitState(_attrMgr, &_docsumState);
    const size_t estimatedChunkSize(std::min(0x200000ul, _docsumState._docsumcnt*0x400ul));
    vespalib::Slime::UP response(std::make_unique<vespalib::Slime>(makeSlimeParams(estimatedChunkSize)));
    Cursor & root = response->setObject();
    Cursor & array = root.setArray(DOCSUMS);
    const Symbol docsumSym = response->insert(DOCSUM);
    IDocsumWriter::ResolveClassInfo rci = _docsumWriter.resolveClassInfo(_docsumState._args.getResultClassName(), _docsumStore.getSummaryClassId());
    for (uint32_t i = 0; (i < _docsumState._docsumcnt); ++i) {
        uint32_t docId = _docsumState._docsumbuf[i];
        Cursor & docSumC = array.addObject();
        ObjectSymbolInserter inserter(docSumC, docsumSym);
        if ((docId != search::endDocId) && !rci.mustSkip) {
            if (_request.expired()) {
                addTimedOut(inserter, _request.getTimeLeft());
            } else {
                _docsumWriter.insertDocsum(rci, docId, &_docsumState, &_docsumStore, *response, inserter);
            }
        }
    }
    return response;
}

DocsumContext::DocsumContext(const DocsumRequest & request,
                             IDocsumWriter & docsumWriter,
                             IDocsumStore & docsumStore,
                             const Matcher::SP & matcher,
                             ISearchContext & searchCtx,
                             IAttributeContext & attrCtx,
                             search::IAttributeManager & attrMgr,
                             SessionManager & sessionMgr) :
    _request(request),
    _docsumWriter(docsumWriter),
    _docsumStore(docsumStore),
    _matcher(matcher),
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
    if (_request.useRootSlime()) {
        return std::make_unique<DocsumReply>(createSlimeReply());
    }
    return createReply();
}

void
DocsumContext::FillSummaryFeatures(search::docsummary::GetDocsumsState * state, search::docsummary::IDocsumEnvironment *)
{
    assert(&_docsumState == state);
    if (_matcher->canProduceSummaryFeatures()) {
        state->_summaryFeatures = _matcher->getSummaryFeatures(_request, _searchCtx, _attrCtx, _sessionMgr);
    }
    state->_summaryFeaturesCached = false;
}

void
DocsumContext::FillRankFeatures(search::docsummary::GetDocsumsState * state, search::docsummary::IDocsumEnvironment *)
{
    assert(&_docsumState == state);
    // check if we are allowed to run
    if ((state->_args.GetQueryFlags() & search::fs4transport::QFLAG_DUMP_FEATURES) == 0) {
        return;
    }
    state->_rankFeatures = _matcher->getRankFeatures(_request, _searchCtx, _attrCtx, _sessionMgr);
}

namespace {
Location *getLocation(const string &loc_str, search::IAttributeManager &attrMgr)
{
    LOG(debug, "Filling document locations from location string: %s", loc_str.c_str());

    Location *loc = new Location;
    string location;
    string::size_type pos = loc_str.find(':');
    if (pos != string::npos) {
        string view = loc_str.substr(0, pos);
        AttributeGuard::UP vec = attrMgr.getAttribute(view);
        if (!vec->valid()) {
            view = PositionDataType::getZCurveFieldName(view);
            vec = attrMgr.getAttribute(view);
        }
        loc->setVecGuard(std::move(vec));
        location = loc_str.substr(pos + 1);
    } else {
        LOG(warning, "Location string lacks attribute vector specification. "
            "loc='%s'", loc_str.c_str());
        location = loc_str;
    }
    loc->parse(location);
    return loc;
}
}  // namespace

void
DocsumContext::ParseLocation(search::docsummary::GetDocsumsState *state)
{
    state->_parsedLocation.reset(getLocation(_request.location, _attrMgr));
}

} // namespace proton
