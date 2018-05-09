// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "datasetcollection.h"
#include "fnet_dataset.h"
#include "fnet_engine.h"
#include "fnet_search.h"
#include "mergehits.h"
#include <vespa/searchlib/engine/packetconverter.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/xxhash/xxhash.h>

#include <vespa/log/log.h>
LOG_SETUP(".fnet_search");

#define IS_MLD_PART(part)           ((part) > mldpartidmask)
#define MLD_PART_TO_PARTID(part)    ((part) & mldpartidmask)
#define ENCODE_MLD_PART(part)       (((part) + 1) << partbits)
#define DECODE_MLD_PART(part)       (((part) >> partbits) - 1)

using fdispatch::SearchPath;
using vespalib::nbostream;
using vespalib::stringref;
using namespace search::fs4transport;
using search::engine::PacketConverter;

//---------------------------------------------------------------------
//

FastS_FNET_SearchNode::FastS_FNET_SearchNode(FastS_FNET_Search *search, uint32_t partid)
    : _search(search),
      _engine(nullptr),
      _channel(nullptr),
      _partid(partid),
      _rowid(0),
      _stamp(0),
      _qresult(nullptr),
      _queryTime(0.0),
      _flags(),
      _docidCnt(0),
      _pendingDocsums(0),
      _docsumRow(0),
      _docsum_offsets_idx(0),
      _docsumTime(0.0),
      _gdx(nullptr),
      _docsum_offsets(),
      _extraDocsumNodes(),
      _nextExtraDocsumNode(this),
      _prevExtraDocsumNode(this),
      _hit_beg(nullptr),
      _hit_cur(nullptr),
      _hit_end(nullptr),
      _sortDataIterator()
{
}


FastS_FNET_SearchNode::~FastS_FNET_SearchNode()
{
    Disconnect();
    if (_qresult != nullptr) {
        _qresult->Free();
    }
    if (_gdx != nullptr) {
        _gdx->Free();
    }
}

FastS_FNET_SearchNode::FastS_FNET_SearchNode(FastS_FNET_SearchNode &&)
{
    // These objects are referenced everywhere and must never be either copied nor moved,
    // but as std::vector requires this to exist so we do this little trick.
    assert(false);
}

bool
FastS_FNET_SearchNode::NT_InitMerge(uint32_t *numDocs,
                                    uint64_t *totalHits,
                                    search::HitRank *maxRank,
                                    uint32_t *sortDataDocs)
{
    uint32_t myNumDocs = 0;
    if (_qresult != nullptr) {
        myNumDocs = _qresult->_numDocs;
        *numDocs += myNumDocs;
        *totalHits += _qresult->_totNumDocs;
        search::HitRank mr = _qresult->_maxRank;
        if (mr > *maxRank)
            *maxRank = mr;
    }
    if (myNumDocs > 0) {
        _hit_beg = _qresult->_hits;
        _hit_cur = _hit_beg;
        _hit_end = _hit_beg + myNumDocs;
        if ((_qresult->_features & search::fs4transport::QRF_SORTDATA) != 0) {
            _sortDataIterator.Init(myNumDocs, _qresult->_sortIndex, _qresult->_sortData);
            *sortDataDocs += myNumDocs;
        }
        return true;
    }
    return false;
}


FastS_EngineBase *
FastS_FNET_SearchNode::getPartition(const std::unique_lock<std::mutex> &dsGuard, bool userow, FastS_FNET_DataSet *dataset)
{
    return ((userow)
        ? dataset->getPartitionMLD(dsGuard, getPartID(), _flags._docsumMld, _docsumRow)
        : dataset->getPartitionMLD(dsGuard, getPartID(), _flags._docsumMld));
}


void
FastS_FNET_SearchNode::
allocGDX(search::docsummary::GetDocsumArgs *args, const search::engine::PropertiesMap &props)
{
    FS4Packet_GETDOCSUMSX *gdx = new FS4Packet_GETDOCSUMSX();

    gdx->AllocateDocIDs(_docidCnt);
    _gdx = gdx;
    _docsum_offsets.resize(_gdx->_docid.size());
    _docsum_offsets_idx = 0;
    if (args == nullptr)
        return;

    if (args->getRankProfile().size() != 0 || args->GetQueryFlags() != 0) {
        gdx->_features |= search::fs4transport::GDF_RANKP_QFLAGS;
        gdx->setRanking(args->getRankProfile());
        gdx->_qflags = args->GetQueryFlags();
    }
    gdx->setTimeout(args->getTimeout());

    if (args->getResultClassName().size() > 0) {
        gdx->_features |= search::fs4transport::GDF_RESCLASSNAME;
        gdx->setResultClassName(args->getResultClassName());
    }

    if (props.size() > 0) {
        PacketConverter::fillPacketProperties(props, gdx->_propsVector);
        gdx->_features |= search::fs4transport::GDF_PROPERTIES;
    }

    if (args->getStackDump().size() > 0) {
        gdx->_features |= search::fs4transport::GDF_QUERYSTACK;
        gdx->_stackItems = args->GetStackItems();
        gdx->setStackDump(args->getStackDump());
    }

    if (args->GetLocationLen() > 0) {
        gdx->_features |= search::fs4transport::GDF_LOCATION;
        gdx->setLocation(args->getLocation());
    }

    if (args->getFlags() != 0) {
        gdx->_features |= search::fs4transport::GDF_FLAGS;
        gdx->_flags = args->getFlags();
    }
}


void
FastS_FNET_SearchNode::postGDX(uint32_t *pendingDocsums, uint32_t *docsumNodes)
{
    FS4Packet_GETDOCSUMSX *gdx = _gdx;
    FastS_assert(gdx->_docid.size() == _docsum_offsets_idx);
    if (_flags._docsumMld) {
        gdx->_features |= search::fs4transport::GDF_MLD;
    }
    if (PostPacket(gdx)) {
        _pendingDocsums = _docsum_offsets_idx;
        *pendingDocsums += _pendingDocsums;
        (*docsumNodes)++;
    }
    _gdx = nullptr; // packet hand-over
    _docsum_offsets_idx = 0;
}


FNET_IPacketHandler::HP_RetCode
FastS_FNET_SearchNode::HandlePacket(FNET_Packet *packet, FNET_Context context)
{
    uint32_t pcode = packet->GetPCODE();
    if (LOG_WOULD_LOG(spam)) {
        LOG(spam, "handling packet %p\npacket=%s", packet, packet->Print().c_str());
        context.Print();
    }
    if (packet->IsChannelLostCMD()) {
        _search->LostSearchNode(this);
    } else if (pcode == search::fs4transport::PCODE_QUERYRESULTX) {
        _search->GotQueryResult(this, (FS4Packet_QUERYRESULTX *) packet);
    } else if (pcode == search::fs4transport::PCODE_DOCSUM) {
        _search->GotDocsum(this, (FS4Packet_DOCSUM *) packet);
    } else if (pcode == search::fs4transport::PCODE_ERROR) {
        _search->GotError(this, static_cast<FS4Packet_ERROR *>(packet));
    } else {
        if (pcode == search::fs4transport::PCODE_EOL) {
            _search->GotEOL(this);
        }
        packet->Free();
    }
    return FNET_KEEP_CHANNEL;
}


FastS_FNET_SearchNode *
FastS_FNET_SearchNode::
allocExtraDocsumNode(bool mld, uint32_t rowid, uint32_t rowbits)
{
    if (_extraDocsumNodes.empty()) {
        size_t sz = (1 << (rowbits + 1));
        _extraDocsumNodes.resize(sz);
    }

    uint32_t idx = (rowid << 1) + (mld ? 1 : 0);

    if (_extraDocsumNodes[idx].get() == nullptr) {
        UP eNode(new FastS_FNET_SearchNode(_search, getPartID()));
        eNode->_docsumRow = rowid;
        eNode->_flags._docsumMld = mld;

        eNode->_nextExtraDocsumNode = this;
        eNode->_prevExtraDocsumNode = _prevExtraDocsumNode;
        _prevExtraDocsumNode->_nextExtraDocsumNode = eNode.get();
        _prevExtraDocsumNode = eNode.get();
        _extraDocsumNodes[idx] = std::move(eNode);
    }
    return _extraDocsumNodes[idx].get();
}


//---------------------------------------------------------------------

void
FastS_FNET_Search::Timeout::PerformTask()
{
    _search->HandleTimeout();
}

//---------------------------------------------------------------------

void
FastS_FNET_Search::reallocNodes(size_t numParts)
{
    _nodes.clear();

    _nodes.reserve(numParts);

    for (uint32_t i = 0; i < numParts; i++) {
        _nodes.emplace_back(this, i);
    }
}

namespace {
volatile std::atomic<uint64_t> _G_prevFixedRow(0);
} //anonymous namespace

uint32_t
FastS_FNET_Search::getFixedRowCandidate()
{
    uint32_t rowId(_dataset->useRoundRobinForFixedRow()
                   ? (_G_prevFixedRow++)
                   : _dataset->getRandomWeightedRow());
    return rowId % _dataset->getNumRows();
}

uint32_t
FastS_FNET_Search::getNextFixedRow()
{
    size_t numTries(0);
    uint32_t fixedRow(0);
    size_t maxTries(_dataset->getNumRows());
    if ( ! _dataset->useRoundRobinForFixedRow()) {
       maxTries *= 10;
    }
    for(;numTries < maxTries; numTries++) {
        fixedRow = getFixedRowCandidate();
        if (_dataset->isGoodRow(fixedRow)) {
            break;
        }
    }
    if (numTries == maxTries) {
        fixedRow = getFixedRowCandidate(); // Will roundrobin/random if all rows are incomplete.
    }
    LOG(debug, "FixedRow: selected=%d, numRows=%d, numTries=%ld, _G_prevFixedRow=%ld", fixedRow, _dataset->getNumRows(), numTries, _G_prevFixedRow.load());
    return fixedRow;
}

void
FastS_FNET_Search::connectNodes(const EngineNodeMap & engines)
{
    for (const auto & pair : engines) {
        if ( ! pair.second->IsConnected() ) {
            // Here we are connecting without having the DataSet lock.
            // This might give a race when nodes go up or down, or there is a config change.
            // However none has ever been detected for as long as the race has existed.
            // The correct fix would be to make the DataSet be constant and be replaced upon changes.
            // And using shared_ptr to them. That would avoid the big global lock all together.
            pair.second->Connect_HasDSLock(pair.first->GetFNETEngine());
        } else {
            pair.first->SubCost();
        }
    }
    _nodesConnected = true;
}

uint32_t
FastS_FNET_Search::getHashedRow() const {
    uint32_t hash = XXH32(&_queryArgs->sessionId[0], _queryArgs->sessionId.size(), 0);
    std::vector<uint32_t> rowIds;
    rowIds.reserve(_dataset->getNumRows());
    for (uint32_t rowId(0); rowId < _dataset->getNumRows(); rowId++) {
        rowIds.push_back(rowId);
    }
    while (!rowIds.empty()) {
        uint32_t index = hash % rowIds.size();
        uint32_t fixedRow = rowIds[index];
        if (_dataset->isGoodRow(fixedRow)) {
            return fixedRow;
        }
        rowIds.erase(rowIds.begin() + index);
    }
    return 0;
}
void
FastS_FNET_Search::ConnectQueryNodes()
{
    FastS_assert( ! _nodes.empty() );
    FastS_assert(!_nodesConnected);

    uint32_t fixedRow(0);
    if (_dataset->useFixedRowDistribution()) {
        fixedRow = (_queryArgs->sessionId.empty()) ? getNextFixedRow() : getHashedRow();
        _fixedRow = fixedRow;
        size_t numParts = _dataset->getNumPartitions(fixedRow);
        if (_nodes.size() > numParts) {
            reallocNodes(numParts);
        }
    }
    EngineNodeMap engines;
    engines.reserve(_nodes.size());
    {
        auto dsGuard(_dataset->getDsGuard());
        for (uint32_t i = 0; i < _nodes.size(); i++) {
            FastS_EngineBase *engine = nullptr;
            if (_dataset->useFixedRowDistribution()) {
                engine = _dataset->getPartition(dsGuard, i, fixedRow);
                LOG(debug, "FixedRow: getPartition(part=%u, row=%u) -> engine(%s)", i, fixedRow, (engine != nullptr ? engine->GetName() : "null"));
            } else {
                engine = _dataset->getPartition(dsGuard, i);
            }
            if (engine != nullptr) {
                LOG(debug, "Wanted part=%d, engine={name=%s, row=%d, partid=%d}", i, engine->GetName(), engine->GetConfRowID(), engine->GetPartID());
                if (engine != nullptr) {
                    engines.emplace_back(engine, getNode(i));
                }
            } else {
                LOG(debug, "No engine for part %d", i);
            }
        }
    }
    connectNodes(engines);
}


void
FastS_FNET_Search::ConnectEstimateNodes()
{
    FastS_assert( ! _nodes.empty() );
    FastS_assert(!_nodesConnected);

    uint32_t partid  = _util.GetQuery().StackDumpHashKey() % _estPartCutoff;
    uint32_t trycnt  = 0;
    uint32_t partcnt = 0;

    EngineNodeMap engines;
    {
        auto dsGuard(_dataset->getDsGuard());
        while (partcnt < _dataset->GetEstimateParts() && trycnt < _estPartCutoff) {
            FastS_EngineBase *engine = _dataset->getPartition(dsGuard, partid);
            if (engine != nullptr) {
                engines.emplace_back(engine, getNode(partid));
                partcnt++;
            }
            trycnt++;
            partid = (partid + 1) % _estPartCutoff;
        }
        _estParts = partcnt;
    }
    connectNodes(engines);
}


void FastS_FNET_SearchNode::Connect(FastS_FNET_Engine *engine)
{
    FastS_assert(_engine == nullptr);
    FastS_assert(_channel == nullptr);

    _engine = engine;
    _flags._needSubCost = true;
    auto dsGuard(_engine->getDsGuard());
    _channel = _engine->OpenChannel_HasDSLock(this);
    _rowid   = _engine->GetConfRowID();
    _stamp   = _engine->GetTimeStamp();
}

void FastS_FNET_SearchNode::Connect_HasDSLock(FastS_FNET_Engine *engine)
{
    _engine = engine;
    _flags._needSubCost = true;
    _channel = _engine->OpenChannel_HasDSLock(this);
    _rowid   = _engine->GetConfRowID();
    _stamp   = _engine->GetTimeStamp();
}


void FastS_FNET_Search::connectSearchPath(const vespalib::string &spec)
{
    FastS_assert( ! _nodes.empty());
    FastS_assert(!_nodesConnected);

    SearchPath searchPath(spec, _nodes.size());
    uint32_t dispatchLevel = _dsc->GetAppContext()->getDispatchLevel();
    LOG(debug, "Looking up searchpath element for dispatch level %u in searchpath '%s' (size=%zu)",
        dispatchLevel, spec.c_str(), searchPath.elements().size());
    if (dispatchLevel < searchPath.elements().size()) {
        connectSearchPath(searchPath.elements()[dispatchLevel], spec, dispatchLevel);
    } else {
        LOG(warning, "Did not find searchpath element for dispatch level "
            "%u in searchpath '%s' (size=%zu). No search nodes will be queried.",
            dispatchLevel, spec.c_str(), searchPath.elements().size());
    }
}

void FastS_FNET_Search::connectSearchPath(const SearchPath::Element &elem,
                                          const vespalib::string &spec,
                                          uint32_t dispatchLevel)
{
    EngineNodeMap engines;
    {
        auto dsGuard(_dataset->getDsGuard());
        if (!elem.hasRow()) {
            for (size_t partId : elem.nodes()) {
                if (partId < _nodes.size()) {
                    FastS_EngineBase *engine = _dataset->getPartition(dsGuard, partId);
                    LOG(debug, "searchpath='%s', partId=%ld, dispatchLevel=%u", spec.c_str(), partId, dispatchLevel);
                    if (engine != nullptr) {
                        engines.emplace_back(engine, getNode(partId));
                    }
                }
            }
        } else {
            for (size_t partId : elem.nodes()) {
                if (partId < _nodes.size()) {
                    FastS_EngineBase *engine = _dataset->getPartition(dsGuard, partId, elem.row());
                    LOG(debug, "searchpath='%s', partId=%ld, row=%ld, dispatchLevel=%u", spec.c_str(), partId, elem.row(), dispatchLevel);
                    if (engine != nullptr) {
                        engines.emplace_back(engine, getNode(partId));
                    }
                }
            }
        }
    }
    connectNodes(engines);
}

void
FastS_FNET_Search::ConnectDocsumNodes(bool ignoreRow)
{
    FastS_assert( ! _nodes.empty());
    if (_nodesConnected)
        return;

    bool userow = (_dataset->GetRowBits() > 0) && !ignoreRow;

    EngineNodeMap engines;
    {
        auto dsGuard(_dataset->getDsGuard());
        for (auto & node : _nodes) {
            if (node._gdx != nullptr) {
                FastS_EngineBase *engine = node.getPartition(dsGuard, userow, _dataset);
                if (engine != nullptr) {
                    engines.emplace_back(engine, &node);
                }
            }
            for (FastS_FNET_SearchNode::ExtraDocsumNodesIter iter(&node); iter.valid(); ++iter) {
                FastS_FNET_SearchNode *eNode = *iter;
                if (eNode->_gdx != nullptr) {
                    FastS_EngineBase *engine = eNode->getPartition(dsGuard, userow, _dataset);
                    if (engine != nullptr) {
                        engines.emplace_back(engine, eNode);
                    }
                }
            }
        }
    }
    connectNodes(engines);
}

void
FastS_FNET_Search::EncodePartIDs(uint32_t partid, uint32_t rowid, bool mld,
                                 FS4Packet_QUERYRESULTX::FS4_hit *pt,
                                 FS4Packet_QUERYRESULTX::FS4_hit *end)
{
    uint32_t rowbits  = _dataset->GetRowBits();
    uint32_t partbits = _dataset->GetPartBits();

    if (rowbits > 0) {
        if (mld) {
            for (; pt < end; pt++) {
                pt->_partid = ((ENCODE_MLD_PART(pt->_partid) + partid) << rowbits) + rowid;
            }
        } else {
            for (; pt < end; pt++) {
                pt->_partid = (partid << rowbits) + rowid;
            }
        }

    } else { // rowbits == 0

        if (mld) {
            for (; pt < end; pt++) {
                pt->_partid = ENCODE_MLD_PART(pt->_partid) + partid;
            }
        } else {
            for (; pt < end; pt++) {
                pt->_partid = partid;
            }
        }
    }
}


FastS_FNET_Search::FastS_FNET_Search(FastS_DataSetCollection *dsc,
                                     FastS_FNET_DataSet *dataset,
                                     FastS_TimeKeeper *timeKeeper)
    : FastS_AsyncSearch(dataset->GetID()),
      _lock(),
      _timeKeeper(timeKeeper),
      _startTime(timeKeeper->GetTime()),
      _timeout(dataset->GetAppContext()->GetFNETScheduler(), this),
      _util(),
      _dsc(dsc),
      _dataset(dataset),
      _datasetActiveCostRef(true),
      _nodes(),
      _nodesConnected(false),
      _estParts(0),
      _estPartCutoff(dataset->GetEstimatePartCutoff()),
      _FNET_mode(FNET_NONE),
      _pendingQueries(0),
      _pendingDocsums(0),
      _pendingDocsumNodes(0),
      _requestedDocsums(0),
      _queryNodes(0),
      _queryNodesTimedOut(0),
      _docsumNodes(0),
      _docsumNodesTimedOut(0),
      _docsumsTimedOut(0),
      _queryTimeout(false),
      _docsumTimeout(false),
      _queryStartTime(0.0),
      _queryMinWait(0.0),
      _queryMaxWait(0.0),
      _queryWaitCalculated(false),
      _adjustedQueryTimeOut(0.0),
      _docSumStartTime(0.0),
      _adjustedDocSumTimeOut(0.0),
      _fixedRow(0),
      _resbuf()
{
    _util.GetQuery().SetDataSet(dataset->GetID());
    _util.SetStartTime(GetTimeKeeper()->GetTime());
    reallocNodes(_dataset->GetPartitions());
}


FastS_FNET_Search::~FastS_FNET_Search()
{
    _timeout.Kill();
    _nodes.clear();
    _util.DropResult();
    dropDatasetActiveCostRef();
}


void
FastS_FNET_Search::dropDatasetActiveCostRef()
{
    if (_datasetActiveCostRef) {
        _dataset->SubCost();
        _dataset->ClearActiveQuery(GetTimeKeeper());
        _datasetActiveCostRef = false;
    }
}


void
FastS_FNET_Search::GotQueryResult(FastS_FNET_SearchNode *node,
                                  FS4Packet_QUERYRESULTX *qrx)
{
    auto searchGuard(BeginFNETWork());
    if (!searchGuard) {
        qrx->Free();
        return;
    }

    if (_FNET_mode == FNET_QUERY &&
        node->_flags._pendingQuery) {
        FastS_assert(node->_qresult == nullptr);
        node->_qresult = qrx;
        EncodePartIDs(node->getPartID(), node->GetRowID(),
                      (qrx->_features & search::fs4transport::QRF_MLD) != 0,
                      qrx->_hits, qrx->_hits + qrx->_numDocs);
        LOG(spam, "Got result from row(%d), part(%d) = hits(%d), numDocs(%" PRIu64 ")", node->GetRowID(), node->getPartID(), qrx->_numDocs, qrx->_totNumDocs);
        node->_flags._pendingQuery = false;
        _pendingQueries--;
        double tnow = GetTimeKeeper()->GetTime();
        node->_queryTime = tnow - _startTime;
        node->GetEngine()->UpdateSearchTime(tnow, node->_queryTime, false);
        adjustQueryTimeout();
        node->dropCost();
    } else {
        qrx->Free();
    }
    EndFNETWork(std::move(searchGuard));
}

void
FastS_FNET_Search::GotDocsum(FastS_FNET_SearchNode *node,
                             FS4Packet_DOCSUM *docsum)
{
    auto searchGuard(BeginFNETWork());
    if (!searchGuard) {
        docsum->Free();
        return;
    }

    if (_FNET_mode == FNET_DOCSUMS &&
        node->_pendingDocsums > 0) {
        LOG(spam, "Got docsum from row(%d), part(%d) = docsumidx(%d)", node->GetRowID(), node->getPartID(), node->_docsum_offsets_idx);
        uint32_t offset = node->_docsum_offsets[node->_docsum_offsets_idx++];
        docsum->swapBuf(_resbuf[offset]._buf);
        node->_pendingDocsums--;
        _pendingDocsums--;
        if (node->_pendingDocsums == 0) {
            node->_docsumTime = (GetTimeKeeper()->GetTime() - _startTime - node->_queryTime);
            _pendingDocsumNodes--;
        }
        adjustDocsumTimeout();
    }
    docsum->Free();
    EndFNETWork(std::move(searchGuard));
}

void
FastS_FNET_Search::LostSearchNode(FastS_FNET_SearchNode *node)
{
    auto searchGuard(BeginFNETWork());
    if (!searchGuard) {
        return;
    }

    if (_FNET_mode == FNET_QUERY && node->_flags._pendingQuery) {
        FastS_assert(_pendingQueries > 0);
        _pendingQueries--;
        node->_flags._pendingQuery = false;
        adjustQueryTimeout();
        node->dropCost();
    } else if (_FNET_mode == FNET_DOCSUMS && node->_pendingDocsums > 0) {
        uint32_t nodePendingDocsums = node->_pendingDocsums;
        FastS_assert(_pendingDocsums >= nodePendingDocsums);
        _pendingDocsums -= nodePendingDocsums;
        node->_pendingDocsums = 0;
        _pendingDocsumNodes--;
        adjustDocsumTimeout();
    }
    EndFNETWork(std::move(searchGuard));
}


void
FastS_FNET_Search::GotEOL(FastS_FNET_SearchNode *node)
{
    auto searchGuard(BeginFNETWork());
    if (!searchGuard) {
        return;
    }

    LOG(spam, "Got EOL from row(%d), part(%d) = pendingQ(%d) pendingDocsum(%d)", node->GetRowID(), node->getPartID(), node->_flags._pendingQuery, node->_pendingDocsums);
    if (_FNET_mode == FNET_QUERY && node->_flags._pendingQuery) {
        FastS_assert(_pendingQueries > 0);
        _pendingQueries--;
        node->_flags._pendingQuery = false;
        adjustQueryTimeout();
        node->dropCost();
    } else if (_FNET_mode == FNET_DOCSUMS && node->_pendingDocsums > 0) {
        uint32_t nodePendingDocsums = node->_pendingDocsums;
        FastS_assert(_pendingDocsums >= nodePendingDocsums);
        _pendingDocsums -= nodePendingDocsums;
        node->_pendingDocsums = 0;
        _pendingDocsumNodes--;
        adjustDocsumTimeout();
    }
    EndFNETWork(std::move(searchGuard));
}


void
FastS_FNET_Search::GotError(FastS_FNET_SearchNode *node,
                            FS4Packet_ERROR *error)
{
    auto searchGuard(BeginFNETWork());
    if (!searchGuard) {
        error->Free();
        return;
    }

    LOG(spam,
        "Got Error from row(%d), part(%d) = pendingQ(%d) pendingDocsum(%d)",
        node->GetRowID(),
        node->getPartID(),
        node->_flags._pendingQuery,
        node->_pendingDocsums);

    if (_FNET_mode == FNET_QUERY && node->_flags._pendingQuery) {
        FastS_assert(_pendingQueries > 0);
        _pendingQueries--;
        node->_flags._pendingQuery = false;
        if (error->_errorCode == search::engine::ECODE_TIMEOUT) {
            node->_flags._queryTimeout = true;
            _queryNodesTimedOut++;
        }
        adjustQueryTimeout();
    } else if (_FNET_mode == FNET_DOCSUMS && node->_pendingDocsums > 0) {
        uint32_t nodePendingDocsums = node->_pendingDocsums;
        FastS_assert(_pendingDocsums >= nodePendingDocsums);
        _pendingDocsums -= nodePendingDocsums;
        node->_pendingDocsums = 0;
        _pendingDocsumNodes--;
        if (error->_errorCode == search::engine::ECODE_TIMEOUT) {
            node->_flags._docsumTimeout = true;
            _docsumNodesTimedOut++;
            _docsumsTimedOut += nodePendingDocsums;
        }
        adjustDocsumTimeout();
    }
    error->Free();
    EndFNETWork(std::move(searchGuard));
}


void
FastS_FNET_Search::HandleTimeout()
{
    auto searchGuard(BeginFNETWork());
    if (!searchGuard) {
        return;
    }

    if (_FNET_mode == FNET_QUERY) {
        for (FastS_FNET_SearchNode & node : _nodes) {
            if (node._flags._pendingQuery) {
                FastS_assert(_pendingQueries > 0);
                _pendingQueries--;
                node._flags._pendingQuery = false;
                node._flags._queryTimeout = true;
                _queryNodesTimedOut++;
                double tnow = GetTimeKeeper()->GetTime();
                node._queryTime = tnow - _startTime;
                node.GetEngine()->UpdateSearchTime(tnow, node._queryTime, true);
            }
        }
        _queryTimeout = true;
    } else if (_FNET_mode == FNET_DOCSUMS) {
        for (FastS_FNET_SearchNode & node : _nodes) {
            if (node._pendingDocsums > 0) {
                uint32_t nodePendingDocsums = node._pendingDocsums;
                FastS_assert(_pendingDocsums >= nodePendingDocsums);
                _pendingDocsums -= nodePendingDocsums;
                _docsumsTimedOut += nodePendingDocsums;
                node._pendingDocsums = 0;
                node._flags._docsumTimeout = true;
                _docsumNodesTimedOut++;
                _pendingDocsumNodes--;
            }
            for (FastS_FNET_SearchNode::ExtraDocsumNodesIter iter(&node); iter.valid(); ++iter) {
                FastS_FNET_SearchNode *eNode = *iter;
                if (eNode->_pendingDocsums > 0) {
                    uint32_t nodePendingDocsums = eNode->_pendingDocsums;
                    FastS_assert(_pendingDocsums >= nodePendingDocsums);
                    _pendingDocsums -= nodePendingDocsums;
                    _docsumsTimedOut += nodePendingDocsums;
                    eNode->_pendingDocsums = 0;
                    eNode->_flags._docsumTimeout = true;
                    _docsumNodesTimedOut++;
                    _pendingDocsumNodes--;
                }
            }
        }
        _docsumTimeout = true;
    }
    EndFNETWork(std::move(searchGuard));
}

std::unique_lock<std::mutex>
FastS_FNET_Search::BeginFNETWork()
{
    std::unique_lock<std::mutex> searchGuard(_lock);
    if (_FNET_mode == FNET_NONE) {
        searchGuard.unlock();
    }
    return searchGuard;
}

void
FastS_FNET_Search::EndFNETWork(std::unique_lock<std::mutex> searchGuard)
{
    if (_FNET_mode == FNET_QUERY && _pendingQueries == 0) {
        _FNET_mode = FNET_NONE;
        searchGuard.unlock();
        _searchOwner->DoneQuery(this);
    } else if (_FNET_mode == FNET_DOCSUMS && _pendingDocsums == 0) {
        _FNET_mode = FNET_NONE;
        searchGuard.unlock();
        _searchOwner->DoneDocsums(this);
    }
}

bool
FastS_FNET_Search::ShouldLimitHitsPerNode() const
{
    return (_util.GetAlignedMaxHits() > _dataset->GetMaxHitsPerNode());
}


void
FastS_FNET_Search::MergeHits()
{
    FastS_HitMerger<FastS_FNETMerge> merger(this);
    merger.MergeHits();

    if (_util.IsEstimate())
        return;

    if (ShouldLimitHitsPerNode())
        _dataset->UpdateMaxHitsPerNodeLog(merger.WasIncomplete(), merger.WasFuzzy());

    if (!_queryArgs->groupSpec.empty()) {
        _groupMerger.reset(new search::grouping::MergingManager(_dataset->GetPartBits(), _dataset->GetRowBits()));
        for (const FastS_FNET_SearchNode & node : _nodes) {
            if (node._qresult != nullptr) {
                _groupMerger->addResult(node.getPartID(), node.GetRowID(),
                                        ((node._qresult->_features & search::fs4transport::QRF_MLD) != 0),
                                        node._qresult->_groupData, node._qresult->_groupDataLen);
            }
        }
        _groupMerger->merge();
        _util.SetGroupResultLen(_groupMerger->getGroupResultLen());
        _util.SetGroupResult(_groupMerger->getGroupResult());
    }
}

void
FastS_FNET_Search::CheckCoverage()
{
    uint64_t covDocs  = 0;
    uint64_t activeDocs  = 0;
    uint64_t soonActiveDocs = 0;
    uint32_t degradedReason = 0;
    uint16_t nodesQueried = 0;
    uint16_t nodesReplied = 0;
    size_t cntNone(0);

    for (const FastS_FNET_SearchNode & node : _nodes) {
        if (node._qresult != nullptr) {
            covDocs  += node._qresult->_coverageDocs;
            activeDocs  += node._qresult->_activeDocs;
            soonActiveDocs += node._qresult->_soonActiveDocs;
            degradedReason |= node._qresult->_coverageDegradeReason;
            nodesQueried += node._qresult->getNodesQueried();
            nodesReplied += node._qresult->getNodesReplied();
        } else {
            nodesQueried++;
            cntNone++;
        }
    }
    const ssize_t missingParts = cntNone - (_dataset->getSearchableCopies() - 1);
    if ((missingParts > 0) && (cntNone != _nodes.size())) {
        // TODO This is a dirty way of anticipating missing coverage.
        // It should be done differently
        activeDocs += missingParts * activeDocs/(_nodes.size() - cntNone);
    }
    _util.SetCoverage(covDocs, activeDocs, soonActiveDocs, degradedReason, nodesQueried, nodesReplied);
}


void
FastS_FNET_Search::CheckQueryTimes()
{
    double   factor    = _dataset->GetSlowQueryLimitFactor();
    double   bias      = _dataset->GetSlowQueryLimitBias();
    double   queryTime = 0.0;
    int      queryCnt  = 0;

    for (const FastS_FNET_SearchNode & node : _nodes) {
        if (node.IsConnected() && node._queryTime > 0.0) {
            queryTime += node._queryTime;
            queryCnt++;
        }
    }

    if (queryCnt == 0)
        return;

    queryTime = queryTime / (double)queryCnt;
    double maxQueryTime = queryTime * factor + bias;

    for (const FastS_FNET_SearchNode & node : _nodes) {
        if (node.IsConnected() && node._queryTime > maxQueryTime) {
            node.GetEngine()->SlowQuery(maxQueryTime, node._queryTime - maxQueryTime, false);
        }
    }
}


void
FastS_FNET_Search::CheckDocsumTimes()
{
    double   factor     = _dataset->GetSlowDocsumLimitFactor();
    double   bias       = _dataset->GetSlowDocsumLimitBias();
    double   docsumTime = 0.0;
    int      docsumCnt  = 0;

    for (const FastS_FNET_SearchNode & node : _nodes) {
        if (node.IsConnected() && node._docsumTime > 0.0) {
            docsumTime += node._docsumTime;
            docsumCnt++;
        }
    }
    if (docsumCnt == 0)
        return;
    docsumTime = docsumTime / (double)docsumCnt;
    double maxDocsumTime = docsumTime * factor + bias;

    for (const FastS_FNET_SearchNode & node : _nodes) {
        if (node.IsConnected() && node._docsumTime > maxDocsumTime) {
            node.GetEngine()->SlowDocsum(maxDocsumTime, node._docsumTime - maxDocsumTime);
        }
        for (FastS_FNET_SearchNode::ExtraDocsumNodesIter iter(&node); iter.valid(); ++iter) {
            FastS_FNET_SearchNode *eNode = *iter;
            if (eNode->IsConnected() && eNode->_docsumTime > maxDocsumTime) {
                eNode->GetEngine()->SlowDocsum(maxDocsumTime, eNode->_docsumTime - maxDocsumTime);
            }
        }
    }
}


void
FastS_FNET_Search::CheckQueryTimeout()
{
    if (_queryNodes != 0 && _queryNodesTimedOut >= _queryNodes)
        SetError(search::engine::ECODE_TIMEOUT, nullptr);
    if (!_queryTimeout)
        return;

    vespalib::string nodeList;
    uint32_t nodeCnt = 0;
    uint32_t printNodes = 10;
    for (const FastS_FNET_SearchNode & node : _nodes) {
        if (node._flags._queryTimeout) {
            if (nodeCnt < printNodes) {
                if (nodeCnt > 0) {
                    nodeList.append(", ");
                }
                nodeList.append(node.GetEngine()->GetName());
            }
            ++nodeCnt;
        }
    }
    if (nodeCnt > printNodes) {
        nodeList.append(", ...");
    }
    vespalib::string query = _util.GetQuery().getPrintableQuery();
    LOG(warning, "%u nodes(%s) timed out during query execution (%s)",
        nodeCnt, nodeList.c_str(), query.c_str());
}


void
FastS_FNET_Search::CheckDocsumTimeout()
{
    if (_docsumNodes != 0 && _docsumNodesTimedOut >= _docsumNodes)
        SetError(search::engine::ECODE_TIMEOUT, nullptr);
    if (!_docsumTimeout)
        return;

    vespalib::string nodeList;
    uint32_t nodeCnt = 0;
    uint32_t printNodes = 10;
    for (const FastS_FNET_SearchNode & node : _nodes) {
        if (node._flags._docsumTimeout) {
            if (nodeCnt < printNodes) {
                if (nodeCnt > 0) {
                    nodeList.append(", ");
                }
                nodeList.append(node.GetEngine()->GetName());
            }
            ++nodeCnt;
        }
        for (FastS_FNET_SearchNode::ExtraDocsumNodesIter iter(&node); iter.valid(); ++iter) {
            FastS_FNET_SearchNode *eNode = *iter;
            if (eNode->_flags._docsumTimeout) {
                if (nodeCnt < printNodes) {
                    if (nodeCnt > 0) {
                        nodeList.append(", ");
                    }
                    nodeList.append(eNode->GetEngine()->GetName());
                }
                ++nodeCnt;
            }
        }
    }
    if (nodeCnt > printNodes) {
        nodeList.append(", ...");
    }
    double elapsed = GetTimeKeeper()->GetTime() - _docSumStartTime;
    LOG(warning, "%u nodes given %1.6f seconds timeout timed out during docsum fetching after %1.6f seconds (%s)",
        nodeCnt, _adjustedDocSumTimeOut, elapsed, nodeList.c_str());
}


FastS_ISearch::RetCode
FastS_FNET_Search::Search(uint32_t searchOffset,
                          uint32_t maxhits, uint32_t minhits)
{
    // minhits is never sent down from dispatch...
    (void) minhits; // ignore

    _util.setSearchRequest(_queryArgs);
    _util.SetupQuery(maxhits, searchOffset);
    if (_util.IsEstimate())
        _util.InitEstimateMode();
    _util.AdjustSearchParameters(_nodes.size());
    _util.AdjustSearchParametersFinal(_nodes.size());

    vespalib::string searchPath;
    const search::fef::Properties & model = _queryArgs->propertiesMap.modelOverrides();
    search::fef::Property searchPathProperty = model.lookup("searchpath");
    if (searchPathProperty.found()) {
        searchPath = searchPathProperty.get();
    }
    _adjustedQueryTimeOut = static_cast<double>(_queryArgs->getTimeLeft().ms()) / 1000.0;
    if ( ! searchPath.empty()) {
        connectSearchPath(searchPath);
    } else if (_util.IsEstimate()) {
        ConnectEstimateNodes();
    } else {
        ConnectQueryNodes();
    }

    // we support error packets
    uint32_t qflags = _util.GetQuery().GetQueryFlags();

    // propagate drop-sortdata flag only if we have single sub-node
    if (_nodes.size() != 1)
        qflags &= ~search::fs4transport::QFLAG_DROP_SORTDATA;

    uint32_t hitsPerNode = ShouldLimitHitsPerNode()
                           ? _dataset->GetMaxHitsPerNode()
                           : _util.GetAlignedMaxHits();

    // set up expected _queryNodes, _pendingQueries and node->_flags._pendingQuery state
    for (FastS_FNET_SearchNode & node : _nodes) {
        if (node.IsConnected()) {
            node._flags._pendingQuery = true;
            _pendingQueries++;
            _queryNodes++;
        }
    }
    size_t num_send_ok = 0; // number of partitions where packet send succeeded
    std::vector<uint32_t> send_failed; // partitions where packet send failed

    // allow FNET responses while requests are being sent
    {
        std::lock_guard<std::mutex> searchGuard(_lock);
        ++_pendingQueries; // add Elephant query node to avoid early query done
        ++_queryNodes;     // add Elephant query node to avoid early query done
        _FNET_mode = FNET_QUERY;
        _queryStartTime = GetTimeKeeper()->GetTime();
        _timeout.Schedule(_adjustedQueryTimeOut);
    }
    FNET_Packet::SP shared(new FS4Packet_PreSerialized(*setupQueryPacket(hitsPerNode, qflags, _queryArgs->propertiesMap)));
    for (uint32_t i = 0; i < _nodes.size(); i++) {
        FastS_FNET_SearchNode & node = _nodes[i];
        if (node.IsConnected()) {
            FNET_Packet::UP qx(new FS4Packet_Shared(shared));
            LOG(spam, "posting packet to node %d='%s'\npacket=%s", i, node.toString().c_str(), qx->Print(0).c_str());
            if (node.PostPacket(qx.release())) {
                ++num_send_ok;
            } else {
                send_failed.push_back(i);
                LOG(debug, "FAILED posting packet to node %d='%s'\npacket=%s", i, node.toString().c_str(), qx->Print(0).c_str());
            }
        }
    }

    // finalize setup and check if query is still in progress
    bool done;
    {
        std::lock_guard<std::mutex> searchGuard(_lock);
        assert(_queryNodes >= _pendingQueries);
        for (uint32_t i: send_failed) {
            // conditional revert of state for failed nodes
            if (_nodes[i]._flags._pendingQuery) {
                _nodes[i]._flags._pendingQuery = false;
                assert(_pendingQueries > 0);
                --_pendingQueries;
                --_queryNodes;
            }
        }
        // revert Elephant query node to allow search to complete
        assert(_pendingQueries > 0);
        --_pendingQueries;
        --_queryNodes;
        done = (_pendingQueries == 0);
        bool all_down = (num_send_ok == 0);
        if (done) {
            _FNET_mode = FNET_NONE;
            if (all_down) {
                SetError(search::engine::ECODE_ALL_PARTITIONS_DOWN, nullptr);
            }
        }
    }

    return (done) ? RET_OK : RET_INPROGRESS;
}

vespalib::string
FastS_FNET_SearchNode::toString() const
{
    vespalib::string s;
    s += vespalib::make_string("{ channel=%p={%d, c=%p='%s'}, partId = %d, rowid=%d }",
                               _channel, _channel->GetID(),
                               _channel->GetConnection(), _channel->GetConnection()->GetSpec(),
                               _partid, _rowid);
    return s;
}


FNET_Packet::UP
FastS_FNET_Search::setupQueryPacket(uint32_t hitsPerNode, uint32_t qflags,
                                    const search::engine::PropertiesMap &properties)
{
    FNET_Packet::UP ret(new FS4Packet_QUERYX());
    FS4Packet_QUERYX & qx = static_cast<FS4Packet_QUERYX &>(*ret);
    qx._features      = search::fs4transport::QF_PARSEDQUERY | search::fs4transport::QF_RANKP;
    qx._offset        = _util.GetAlignedSearchOffset();
    qx._maxhits       = hitsPerNode; // capped maxhits
    qx.setQueryFlags(qflags);
    qx.setTimeout(_queryArgs->getTimeLeft());

    qx.setRanking(_queryArgs->ranking);

    if (!_queryArgs->sortSpec.empty()) {
        qx._features |= search::fs4transport::QF_SORTSPEC;
        qx.setSortSpec(_queryArgs->sortSpec);
    }

    if (!_queryArgs->groupSpec.empty()) {
        qx._features |= search::fs4transport::QF_GROUPSPEC;
        qx.setGroupSpec(vespalib::stringref(&_queryArgs->groupSpec[0], _queryArgs->groupSpec.size()));
    }

    if (!_queryArgs->sessionId.empty()) {
        qx._features |= search::fs4transport::QF_SESSIONID;
        qx.setSessionId(vespalib::stringref(&_queryArgs->sessionId[0], _queryArgs->sessionId.size()));
    }

    if (!_queryArgs->location.empty()) {
        qx._features |= search::fs4transport::QF_LOCATION;
        qx.setLocation(_queryArgs->location);
    }

    if (properties.size() > 0) {
        PacketConverter::fillPacketProperties(properties, qx._propsVector);
        qx._features |= search::fs4transport::QF_PROPERTIES;
    }

    qx._numStackItems = _queryArgs->stackItems;
    qx.setStackDump(_queryArgs->getStackRef());
    return ret;
}


FastS_ISearch::RetCode
FastS_FNET_Search::ProcessQueryDone()
{
    CheckCoverage();

    if (_errorCode == search::engine::ECODE_NO_ERROR) {
        MergeHits();
    }
    memcpy(&_queryResult, _util.GetQueryResult(), sizeof(FastS_QueryResult));
    double tnow = GetTimeKeeper()->GetTime();
    _queryResult._queryResultTime = tnow - _startTime;
    if (_errorCode == search::engine::ECODE_NO_ERROR) {
        if (_util.IsEstimate()) {
            _dataset->UpdateEstimateCount();
        } else {
            _dataset->UpdateSearchTime(tnow, _queryResult._queryResultTime, _queryNodesTimedOut != 0);
        }
        if ( _dataset->useFixedRowDistribution() ) {
            _dataset->updateSearchTime(_queryResult._queryResultTime, _fixedRow);
        }
    }
    CheckQueryTimes();
    CheckQueryTimeout();
    dropDatasetActiveCostRef();
    return RET_OK;
}


FastS_ISearch::RetCode
FastS_FNET_Search::GetDocsums(const FastS_hitresult *hits, uint32_t hitcnt)
{
    if (hitcnt > 0) {
        _resbuf.resize(hitcnt);
    }

    // copy values from query result

    uint32_t i;
    for (i = 0; i < hitcnt; i++) {
        _resbuf[i]._docid     = 0;
        _resbuf[i]._gid       = hits[i]._gid;
        _resbuf[i]._metric    = hits[i]._metric;
        _resbuf[i]._partition = hits[i]._partition;
    }

    // determine docsum distribution among nodes

    const FastS_hitresult *p = hits;
    uint32_t rowbits = _dataset->GetRowBits();
    uint32_t partbits = _dataset->GetPartBits();
    uint32_t mldpartidmask = (1 << partbits) - 1;
    bool ignoreRow = (_docsumArgs->getFlags() &
                      search::fs4transport::GDFLAG_IGNORE_ROW) != 0;
    if (rowbits > 0) {
        uint32_t rowmask = (1 << rowbits) - 1;
        for (i = 0; i < hitcnt; i++, p++) {
            FastS_FNET_SearchNode *node;
            uint32_t partid0 = p->_partition >> rowbits;
            uint32_t row = ignoreRow ? 0u : p->_partition & rowmask;
            if (IS_MLD_PART(partid0)) {
                uint32_t partid = MLD_PART_TO_PARTID(partid0);
                if (partid < _nodes.size()) {
                    node = getNode(partid);
                    if (node->_docidCnt == 0) {
                        node->_flags._docsumMld = true;// Only accept MLD from now on
                        node->_docsumRow = row;
                    } else if (!node->_flags._docsumMld || row != node->_docsumRow) {
                        if (_nodesConnected)
                            continue;           // Drop (inconsistent)
                        node = node->allocExtraDocsumNode(true, row, rowbits);
                    }
                    node->_docidCnt++;
                }
            } else { // !MLD
                if (partid0 < _nodes.size()) {
                    node = getNode(partid0);
                    if (node->_docidCnt == 0) {
                        node->_docsumRow = row;
                    } else if (node->_flags._docsumMld || row != node->_docsumRow) {
                        if (_nodesConnected)
                            continue;           // Drop (inconsistent)
                        node = node->allocExtraDocsumNode(false, row, rowbits);
                    }
                    node->_docidCnt++;
                }
            }
        }
    } else { // rowbits == 0
        for (i = 0; i < hitcnt; i++, p++) {
            FastS_FNET_SearchNode *node;
            if (IS_MLD_PART(p->_partition)) {
                uint32_t partid = MLD_PART_TO_PARTID(p->_partition);
                if (partid < _nodes.size()) {
                    node = getNode(partid);
                    if (node->_docidCnt == 0) {
                        node->_flags._docsumMld = true;// Only accept MLD from now on
                    } else if (!node->_flags._docsumMld) {
                        if (_nodesConnected)
                            continue;           // Drop (inconsistent)
                        node = node->allocExtraDocsumNode(true, 0, 0);
                    }
                    node->_docidCnt++;
                }
            } else { // !MLD
                if (p->_partition < _nodes.size()) {
                    node = getNode(p->_partition);
                    if (node->_docidCnt == 0) {
                    } else if (node->_flags._docsumMld) {
                        if (_nodesConnected)
                            continue;           // Drop (inconsistent)
                        node = node->allocExtraDocsumNode(false, 0, 0);
                    }
                    node->_docidCnt++;
                }
            }
        }
    }
    FastS_assert(p == hits + hitcnt);

    // allocate docsum requests and insert features

    search::docsummary::GetDocsumArgs *args = _docsumArgs;
    for (FastS_FNET_SearchNode & node : _nodes) {
        if (node._docidCnt != 0) {
            node.allocGDX(args, args->propertiesMap());
        }
        for (FastS_FNET_SearchNode::ExtraDocsumNodesIter iter(&node); iter.valid(); ++iter) {
            FastS_FNET_SearchNode *eNode = *iter;
            if (eNode->_docidCnt != 0)
                eNode->allocGDX(args, args->propertiesMap());
        }
    }

    // fill docid(/partid/stamp) data into docsum requests

    p = hits;
    if (rowbits > 0) {
        uint32_t rowmask = (1 << rowbits) - 1;
        for (i = 0; i < hitcnt; i++, p++) {
            FastS_FNET_SearchNode *node;
            uint32_t partid0 = p->_partition >> rowbits;
            uint32_t row = ignoreRow ? 0u : p->_partition & rowmask;
            if (IS_MLD_PART(partid0)) {
                uint32_t partid = MLD_PART_TO_PARTID(partid0);
                if (partid < _nodes.size()) {
                    node = getNode(partid);
                    if (!node->_flags._docsumMld || row != node->_docsumRow) {
                        if (_nodesConnected)
                            continue;           // Drop (inconsistent)
                        node = node->allocExtraDocsumNode(true, row, rowbits);
                    }

                    FS4Packet_GETDOCSUMSX::FS4_docid &q = node->_gdx->_docid[node->_docsum_offsets_idx];
                    q._gid      = p->_gid;
                    q._partid   = DECODE_MLD_PART(partid0);
                    node->_docsum_offsets[node->_docsum_offsets_idx++] = i;
                }
            } else { // !MLD
                if (partid0 < _nodes.size()) {
                    node = getNode(partid0);
                    if (node->_flags._docsumMld || row != node->_docsumRow) {
                        if (_nodesConnected)
                            continue;           // Drop (inconsistent)
                        node = node->allocExtraDocsumNode(false, row, rowbits);
                    }

                    FS4Packet_GETDOCSUMSX::FS4_docid &q = node->_gdx->_docid[node->_docsum_offsets_idx];
                    q._gid   = p->_gid;
                    node->_docsum_offsets[node->_docsum_offsets_idx++] = i;
                }
            }
        }
    } else { // rowbits == 0
        for (i = 0; i < hitcnt; i++, p++) {
            FastS_FNET_SearchNode *node;
            if (IS_MLD_PART(p->_partition)) {
                uint32_t partid = MLD_PART_TO_PARTID(p->_partition);
                if (partid < _nodes.size()) {
                    node = getNode(partid);
                    if (!node->_flags._docsumMld) {
                        if (_nodesConnected)
                            continue;           // Drop (inconsistent)
                        node = node->allocExtraDocsumNode(true, 0, 0);
                    }

                    FS4Packet_GETDOCSUMSX::FS4_docid &q = node->_gdx->_docid[node->_docsum_offsets_idx];
                    q._gid      = p->_gid;
                    q._partid   = DECODE_MLD_PART(p->_partition);
                    node->_docsum_offsets[node->_docsum_offsets_idx++] = i;
                }
            } else { // !MLD
                if (p->_partition < _nodes.size()) {
                    node = getNode(p->_partition);
                    if (node->_flags._docsumMld) {
                        if (_nodesConnected)
                            continue;           // Drop (inconsistent)
                        node = node->allocExtraDocsumNode(false, 0, 0);
                    }

                    FS4Packet_GETDOCSUMSX::FS4_docid &q = node->_gdx->_docid[node->_docsum_offsets_idx];
                    q._gid   = p->_gid;
                    node->_docsum_offsets[node->_docsum_offsets_idx++] = i;
                }
            }
        }
    }
    FastS_assert(p == hits + hitcnt);

    ConnectDocsumNodes(ignoreRow);
    bool done;
    {
        std::lock_guard<std::mutex> searchGuard(_lock);

        // patch in engine dependent features and send docsum requests

        for (FastS_FNET_SearchNode & node : _nodes) {
            if (node._gdx != nullptr)
                node.postGDX(&_pendingDocsums, &_docsumNodes);
            for (FastS_FNET_SearchNode::ExtraDocsumNodesIter iter(&node); iter.valid(); ++iter) {
                FastS_FNET_SearchNode *eNode = *iter;
                if (eNode->_gdx != nullptr)
                    eNode->postGDX(&_pendingDocsums, &_docsumNodes);
            }
        }
        _pendingDocsumNodes = _docsumNodes;
        _requestedDocsums = _pendingDocsums;

        done = (_pendingDocsums == 0);
        if (!done) {
            _FNET_mode = FNET_DOCSUMS; // FNET; do your thing

            _adjustedDocSumTimeOut = args->getTimeout().sec();
            _docSumStartTime = GetTimeKeeper()->GetTime();
            _timeout.Schedule(_adjustedDocSumTimeOut);
        }
    }

    return (done) ? RET_OK : RET_INPROGRESS;
}


FastS_ISearch::RetCode
FastS_FNET_Search::ProcessDocsumsDone()
{
    _docsumsResult._fullresult      = &_resbuf[0];
    _docsumsResult._fullResultCount = _resbuf.size();
    _docsumsResult._queryDocSumTime = GetTimeKeeper()->GetTime() - _startTime;
    CheckDocsumTimes();
    CheckDocsumTimeout();
    dropDatasetActiveCostRef();
    return RET_OK;
}


void
FastS_FNET_Search::adjustQueryTimeout()
{
    uint32_t pendingQueries = getPendingQueries();

    if (pendingQueries == 0 || _util.IsQueryFlagSet(search::fs4transport::QFLAG_DUMP_FEATURES)) {
        return;
    }

    double mincoverage = _dataset->getMinimalSearchCoverage();
    uint32_t wantedAnswers = getRequestedQueries();
    if (mincoverage < 100.0) {
        wantedAnswers *= mincoverage / 100.0;
        LOG(spam, "Adjusting wanted answers from %u to %u", getRequestedQueries(), wantedAnswers);
    }
    if (getDoneQueries() < wantedAnswers) {
        return;
    }
    if (!_queryWaitCalculated) {
        double timeLeft = _queryArgs->getTimeLeft().sec();
        _queryMinWait = timeLeft * _dataset->getHigherCoverageMinSearchWait();
        _queryMaxWait = timeLeft * _dataset->getHigherCoverageMaxSearchWait();
        _queryWaitCalculated = true;
    }

    double basewait = 0.0;
    double minwait = _queryMinWait;
    double maxwait = _queryMaxWait;

    double elapsed = GetTimeKeeper()->GetTime() - _queryStartTime;

    double missWidth = ((100.0 - mincoverage) * getRequestedQueries()) / 100.0 - 1.0;

    double slopedwait = minwait;

    if (pendingQueries > 1 && missWidth > 0.0)
        slopedwait += ((maxwait - minwait) * (pendingQueries - 1)) / missWidth;

    double newTimeOut = std::max(elapsed, basewait) + slopedwait;


    if (newTimeOut >= _adjustedQueryTimeOut)
        return;

    _adjustedQueryTimeOut = newTimeOut;
    if (newTimeOut > elapsed)
        _timeout.Schedule(newTimeOut - elapsed);
    else
        _timeout.ScheduleNow();
}


void
FastS_FNET_Search::adjustDocsumTimeout()
{
    uint32_t pendingDocsums = getPendingDocsums();

    if (pendingDocsums == 0 || _util.IsQueryFlagSet(search::fs4transport::QFLAG_DUMP_FEATURES)) {
        return;
    }

    double coverage = static_cast<double>(getDoneDocsums() * 100) / getRequestedDocsums();

    double mincoverage = _dataset->getMinimalDocSumCoverage();

    if (coverage < mincoverage)
        return;

    double basewait = _dataset->getHigherCoverageBaseDocSumWait();
    double minwait = _dataset->getHigherCoverageMinDocSumWait();
    double maxwait = _dataset->getHigherCoverageMaxDocSumWait();

    double elapsed = GetTimeKeeper()->GetTime() - _docSumStartTime;

    double missWidth = ((100.0 - mincoverage) * getRequestedDocsums()) / 100.0 - 1.0;

    double slopedwait = minwait;

    if (pendingDocsums > 1 && missWidth > 0.0)
        slopedwait += ((maxwait - minwait) * (pendingDocsums - 1)) / missWidth;

    double newTimeOut = std::max(elapsed, basewait) + slopedwait;

    if (newTimeOut >= _adjustedDocSumTimeOut)
        return;

    _adjustedDocSumTimeOut = newTimeOut;
    if (newTimeOut > elapsed)
        _timeout.Schedule(newTimeOut - elapsed);
    else
        _timeout.ScheduleNow();
}


FastS_Sync_FNET_Search::~FastS_Sync_FNET_Search() {}
