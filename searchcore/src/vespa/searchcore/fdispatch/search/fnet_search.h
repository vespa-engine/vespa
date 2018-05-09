// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/fnet.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/searchcore/fdispatch/common/search.h>
#include <vespa/searchlib/common/sortdata.h>
#include <vespa/searchcore/grouping/mergingmanager.h>
#include <vespa/searchcore/fdispatch/search/search_path.h>
#include <vespa/searchcore/fdispatch/search/querycacheutil.h>
#include <vespa/searchcore/fdispatch/search/fnet_engine.h>

class FastS_FNET_Engine;
class FastS_FNET_Search;

using search::fs4transport::FS4Packet_QUERYRESULTX;
using search::fs4transport::FS4Packet_GETDOCSUMSX;
using search::fs4transport::FS4Packet_DOCSUM;
using search::fs4transport::FS4Packet_TRACEREPLY;

//-----------------------------------------------------------------

class FastS_FNET_SearchNode : public FNET_IPacketHandler
{
public:
    class ExtraDocsumNodesIter;
    typedef std::unique_ptr<FastS_FNET_SearchNode> UP;
private:
    friend class ExtraDocsumNodesIter;

    FastS_FNET_Search       *_search;   // we are part of this search
    FastS_FNET_Engine       *_engine;   // we use this search engine
    FNET_Channel            *_channel;  // connection with search engine
    uint32_t                 _partid;   // engine partition id
    uint32_t                 _rowid;    // engine row id
    uint32_t                 _stamp;    // engine timestamp

public:

    FS4Packet_QUERYRESULTX *_qresult; // query result packet
    double                  _queryTime;
    struct Flags {
        Flags() :
            _pendingQuery(false),
            _docsumMld(false),
            _queryTimeout(false),
            _docsumTimeout(false),
            _needSubCost(false)
        { }
        bool  _pendingQuery;   // is query pending ?
        bool  _docsumMld;
        bool  _queryTimeout;
        bool  _docsumTimeout;
        bool  _needSubCost;
    };

    Flags       _flags;

// Docsum related stuff.
    uint32_t    _docidCnt;
    uint32_t    _pendingDocsums; // how many docsums pending ?
    uint32_t    _docsumRow;
    uint32_t    _docsum_offsets_idx;
    double      _docsumTime;

    FS4Packet_GETDOCSUMSX  *_gdx;
    std::vector<uint32_t>   _docsum_offsets;
private:
    std::vector<FastS_FNET_SearchNode::UP> _extraDocsumNodes;
    FastS_FNET_SearchNode *_nextExtraDocsumNode;
    FastS_FNET_SearchNode *_prevExtraDocsumNode;
public:

// Query processing stuff.
    FS4Packet_QUERYRESULTX::FS4_hit *_hit_beg; // hit array start
    FS4Packet_QUERYRESULTX::FS4_hit *_hit_cur; // current hit
    FS4Packet_QUERYRESULTX::FS4_hit *_hit_end; // end boundary

    search::common::SortDataIterator _sortDataIterator;

public:
    FastS_FNET_SearchNode(FastS_FNET_Search *search, uint32_t partid);
    // These objects are referenced everywhere and must never be either copied nor moved,
    // but std::vector requires this to exist. If called it will assert.
    FastS_FNET_SearchNode(FastS_FNET_SearchNode && rhs);
    FastS_FNET_SearchNode(const FastS_FNET_SearchNode &) = delete;
    FastS_FNET_SearchNode& operator=(const FastS_FNET_SearchNode &) = delete;

    ~FastS_FNET_SearchNode() override;

    // Methods needed by mergehits
    bool NT_InitMerge(uint32_t *numDocs, uint64_t *totalHits, search::HitRank *maxRank, uint32_t *sortDataDocs);
    search::common::SortDataIterator *NT_GetSortDataIterator() { return &_sortDataIterator; }
    FS4Packet_QUERYRESULTX::FS4_hit *NT_GetHit() const { return _hit_cur; }
    uint32_t NT_GetNumHitsUsed() const { return (_hit_cur - _hit_beg); }
    uint32_t NT_GetNumHitsLeft() const { return (_hit_end - _hit_cur); }
    uint64_t NT_GetTotalHits() const { return (_qresult != nullptr) ? _qresult->_totNumDocs : 0; }
    uint32_t NT_GetNumHits() const { return (_hit_end - _hit_beg); }
    void NT_NextHit() { _hit_cur++; }

    uint32_t getPartID() const     { return _partid; }
    uint32_t GetRowID() const     { return _rowid; }

    FastS_FNET_SearchNode * allocExtraDocsumNode(bool mld, uint32_t rowid, uint32_t rowbits);

    FastS_FNET_Engine *GetEngine() const { return _engine; }

    bool IsConnected() const { return _channel != nullptr; }
    void Connect(FastS_FNET_Engine *engine);
    void Connect_HasDSLock(FastS_FNET_Engine *engine);
    FastS_EngineBase * getPartition(const std::unique_lock<std::mutex> &dsGuard, bool userow, FastS_FNET_DataSet *dataset);
    void allocGDX(search::docsummary::GetDocsumArgs *args, const search::engine::PropertiesMap &properties);
    void postGDX(uint32_t *pendingDocsums, uint32_t *pendingDocsumNodes);
    vespalib::string toString() const;

    void dropCost() {
        if (_engine != nullptr && _flags._needSubCost) {
            _engine->SubCost();
            _flags._needSubCost = false;
        }
    }


    void Disconnect()
    {
        if (_channel != nullptr) {
            _channel->CloseAndFree();
            _channel = nullptr;
        }
        if (_engine != nullptr) {
            if (_flags._needSubCost) {
                _engine->SubCost();
                _flags._needSubCost = false;
            }
            _engine = nullptr;
        }
    }


    bool PostPacket(FNET_Packet *packet) {
        return (_channel == nullptr) ?  packet->Free(), false : _channel->Send(packet);
    }

    HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context) override;
};


class FastS_FNET_SearchNode::ExtraDocsumNodesIter
{
private:
    ExtraDocsumNodesIter(const ExtraDocsumNodesIter &other);
    ExtraDocsumNodesIter& operator=(const ExtraDocsumNodesIter &other);

    FastS_FNET_SearchNode       *_cur;
    const FastS_FNET_SearchNode *_head;

public:
    ExtraDocsumNodesIter(const FastS_FNET_SearchNode *head)
        : _cur(head->_nextExtraDocsumNode),
          _head(head)
    {
    }

    ExtraDocsumNodesIter & operator++() {
        _cur = _cur->_nextExtraDocsumNode;
        return *this;
    }

    bool valid() const { return _cur != _head; }
    FastS_FNET_SearchNode *operator*() const { return _cur; }
};


//-----------------------------------------------------------------

class FastS_FNET_Search : public FastS_AsyncSearch
{
private:
    FastS_FNET_Search(const FastS_FNET_Search &);
    FastS_FNET_Search& operator=(const FastS_FNET_Search &);

public:

    class Timeout : public FNET_Task
    {
    private:
        Timeout(const Timeout &);
        Timeout& operator=(const Timeout &);

        FastS_FNET_Search *_search;

    public:
        Timeout(FNET_Scheduler *scheduler, FastS_FNET_Search *search)
            : FNET_Task(scheduler),
              _search(search) {}
        void PerformTask() override;
    };

    enum FNETMode {
        FNET_NONE    = 0x00,
        FNET_QUERY   = 0x01,
        FNET_DOCSUMS = 0x02
    };

private:
    std::mutex               _lock;
    FastS_TimeKeeper        *_timeKeeper;
    double                   _startTime;
    Timeout                  _timeout;
    FastS_QueryCacheUtil     _util;
    std::unique_ptr<search::grouping::MergingManager> _groupMerger;
    FastS_DataSetCollection *_dsc;  // owner keeps this alive
    FastS_FNET_DataSet      *_dataset;
    bool                     _datasetActiveCostRef;
    std::vector<FastS_FNET_SearchNode> _nodes;
    bool                     _nodesConnected;

    uint32_t                 _estParts;
    uint32_t                 _estPartCutoff;

    FNETMode                 _FNET_mode;

    uint32_t                 _pendingQueries;   // # nodes with query left
    uint32_t                 _goodQueries;  // # queries good
    uint32_t                 _pendingDocsums;   // # docsums left
    uint32_t                 _pendingDocsumNodes; // # nodes with docsums left
    uint32_t                 _requestedDocsums; // # docsums requested
    uint32_t                 _goodDocsums;  // # docsums good
    uint32_t                 _queryNodes;     // #nodes with query
    uint32_t                 _queryNodesTimedOut;  // #nodes with query timeout
    uint32_t                 _docsumNodes;    // #nodes with docsums
    uint32_t                 _docsumNodesTimedOut; // #nodes with docsum timeout
    uint32_t                 _docsumsTimedOut;
    bool                     _queryTimeout;
    bool                     _docsumTimeout;

    double                   _queryStartTime;
    double                   _queryMinWait;
    double                   _queryMaxWait;
    bool                     _queryWaitCalculated;
    double                   _adjustedQueryTimeOut;
    double                   _docSumStartTime;
    double                   _adjustedDocSumTimeOut;
    uint32_t                 _fixedRow;

    std::vector<FastS_fullresult>  _resbuf;

    void dropDatasetActiveCostRef();

    typedef std::vector<std::pair<FastS_EngineBase *, FastS_FNET_SearchNode *>> EngineNodeMap;
    void connectNodes(const EngineNodeMap & engines);
    void reallocNodes(size_t numParts);
    void ConnectQueryNodes();
    void ConnectEstimateNodes();
    void connectSearchPath(const vespalib::string &spec);
    void connectSearchPath(const fdispatch::SearchPath::Element &elem,
                           const vespalib::string &spec, uint32_t dispatchLevel);
    void ConnectDocsumNodes(bool ignoreRow);
    uint32_t getNextFixedRow();
    uint32_t getFixedRowCandidate();
    uint32_t getHashedRow() const;

    std::unique_lock<std::mutex> BeginFNETWork();
    void EndFNETWork(std::unique_lock<std::mutex> searchGuard);

    void EncodePartIDs(uint32_t partid, uint32_t rowid, bool mld,
                       FS4Packet_QUERYRESULTX::FS4_hit *pt,
                       FS4Packet_QUERYRESULTX::FS4_hit *end);

    FastS_TimeKeeper *GetTimeKeeper() const { return _timeKeeper; }

    FastS_FNET_SearchNode * getNode(size_t i) { return &_nodes[i]; }
public:
    FastS_FNET_Search(FastS_DataSetCollection *dsc, FastS_FNET_DataSet *dataset, FastS_TimeKeeper *timeKeeper);
    virtual ~FastS_FNET_Search();

    void GotQueryResult(FastS_FNET_SearchNode *node, FS4Packet_QUERYRESULTX *qrx);
    void GotDocsum(FastS_FNET_SearchNode *node, FS4Packet_DOCSUM *docsum);
    void LostSearchNode(FastS_FNET_SearchNode *node);
    void GotEOL(FastS_FNET_SearchNode *node);
    void GotError(FastS_FNET_SearchNode *node, search::fs4transport::FS4Packet_ERROR *error);

    void HandleTimeout();

    bool ShouldLimitHitsPerNode() const;
    void MergeHits();
    void CheckCoverage();
    void CheckQueryTimes();
    void CheckDocsumTimes();
    void CheckQueryTimeout();
    void CheckDocsumTimeout();

    // *** API methods -- BEGIN ***

    FastS_SearchInfo *GetSearchInfo() override { return _util.GetSearchInfo(); }

    RetCode Search(uint32_t searchOffset, uint32_t maxhits, uint32_t minhits = 0) override;
    RetCode ProcessQueryDone() override;
    RetCode GetDocsums(const FastS_hitresult *hits, uint32_t hitcnt) override;
    RetCode ProcessDocsumsDone() override;

    // *** API methods -- END ***

    // Hit merging methods

    FastS_FNET_SearchNode *ST_GetNode(size_t i) { return getNode(i); }
    uint32_t ST_GetNumNodes() const { return _nodes.size(); }
    bool ST_IsEstimate() const { return _util.IsEstimate(); }
    uint32_t ST_GetEstParts() const { return _estParts; }
    uint32_t ST_GetEstPartCutoff() const { return _estPartCutoff; }
    bool ST_ShouldDropSortData() const { return _util.ShouldDropSortData(); }
    bool ST_ShouldLimitHitsPerNode() const { return ShouldLimitHitsPerNode(); }
    void ST_SetNumHits(uint32_t numHits) {
        _util.SetAlignedHitCount(numHits);
        _util.CalcHitCount();
        _util.AllocAlignedHitBuf();
    }
    void ST_AdjustNumHits(uint32_t numHits) {
        _util.SetAlignedHitCount(numHits);
        _util.CalcHitCount();
    }
    uint32_t ST_GetAlignedSearchOffset() const { return _util.GetAlignedSearchOffset(); }
    uint32_t ST_GetAlignedMaxHits() const { return _util.GetAlignedMaxHits(); }
    uint32_t ST_GetAlignedHitCount() const { return _util.GetAlignedHitCount(); }
    FastS_hitresult *ST_GetAlignedHitBuf() { return _util.GetAlignedHitBuf(); }
    FastS_hitresult *ST_GetAlignedHitBufEnd() { return _util.GetAlignedHitBufEnd(); }
    void ST_AllocSortData(uint32_t len) { _util.AllocSortData(len); }
    uint32_t *ST_GetSortIndex() { return _util.GetSortIndex(); }
    char *ST_GetSortData() { return _util.GetSortData(); }
    FastS_QueryResult *ST_GetQueryResult() { return _util.GetQueryResult(); }

    void adjustQueryTimeout();
    void adjustDocsumTimeout();
    uint32_t getRequestedQueries() const { return _queryNodes; }
    uint32_t getPendingQueries() const { return _pendingQueries; }
    uint32_t getDoneQueries() const {
        return getRequestedQueries() - getPendingQueries();
    }
    uint32_t getRequestedDocsums() const { return _requestedDocsums; }
    uint32_t getPendingDocsums() const { return _pendingDocsums; }
    uint32_t getDoneDocsums() const {
        return getRequestedDocsums() - getPendingDocsums();
    }

    FNET_Packet::UP
    setupQueryPacket(uint32_t hitsPerNode, uint32_t qflags,
                     const search::engine::PropertiesMap &properties);
};

//-----------------------------------------------------------------------------

class FastS_Sync_FNET_Search : public FastS_SyncSearchAdapter
{
private:
    FastS_FNET_Search _search;

public:
    FastS_Sync_FNET_Search(FastS_DataSetCollection *dsc, FastS_FNET_DataSet *dataset, FastS_TimeKeeper *timeKeeper) :
        FastS_SyncSearchAdapter(&_search),
        _search(dsc, dataset, timeKeeper)
    {
        _search.SetAsyncArgs(this);
    }
    ~FastS_Sync_FNET_Search() override;
    void Free() override { delete this; }
};

//-----------------------------------------------------------------

