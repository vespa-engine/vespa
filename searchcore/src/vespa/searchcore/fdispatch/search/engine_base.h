// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/searchcore/fdispatch/common/timestat.h>
#include "plain_dataset.h"
#include "poss_count.h"
#include <atomic>

class FastS_FNET_DataSet;
class FastS_DataSetInfo;

class FastS_FNET_Engine;
class FastS_RPC_Engine;

class FastS_EngineBase
{
    friend class FastS_FNET_Engine;
    friend class FastS_RPC_Engine;
    friend class FastS_PlainDataSet;
    friend class FastS_FNET_DataSet;
    friend class FastS_PartitionMap;
    friend class FastS_DataSetInfo;

private:
    FastS_EngineBase(const FastS_EngineBase &);
    FastS_EngineBase& operator=(const FastS_EngineBase &);

public:

    //----------------------------------------------------------------
    // class holding various statistics for a search node
    //----------------------------------------------------------------
    class stats_t
    {
    public:
        enum {
            _queuestatsize       = 100
        };

        // the node goes up and down...
        FastOS_Time _fliptime;	  // When state changed last to UP or big chg
        FastOS_Time _floptime;	  // When state changed last from UP

        // search/docsum slowness
        uint32_t    _slowQueryCnt;
        uint32_t    _slowDocsumCnt;
        double      _slowQuerySecs;
        double      _slowDocsumSecs;

        // active cnt + queue len sampling
        uint32_t _queueLenSampleAcc;  // sum of reported queue lengths
        uint32_t _queueLenSampleCnt;  // number of reported queue lengths
        uint32_t _activecntSampleAcc; // sum of our "load"
        uint32_t _activecntSampleCnt; // number of our "load" samples

        // sampled active cnt + queue len
        struct {
            double _queueLen;
            double _activecnt;
        } _queueLens[_queuestatsize];
        double   _queueLenAcc;
        double   _activecntAcc;
        uint32_t _queueLenIdx;
        uint32_t _queueLenValid;

        stats_t();

    };

    //----------------------------------------------------------------
    // class holding values reported from the node below
    //----------------------------------------------------------------
    class reported_t
    {
    private:
        reported_t(const reported_t &);
        reported_t& operator=(const reported_t &);

    public:
        uint32_t    _queueLen;        // queue len on search node
        uint32_t    _dispatchers;     // # dispatchers using search node

        bool        _mld;
        uint32_t    _reportedPartID;  // Partid reported from node below
        uint32_t    _actNodes;        // From _MLD_MON. # active nodes, or 1
        uint32_t    _maxNodes;        // From _MLD_MON. total # nodes, or 1
        uint32_t    _actParts;        // From _MLD_MON. # active parts, or 1
        uint32_t    _maxParts;        // From _MLD_MON. total # parts, or 1
        PossCount   _activeDocs;
        time_t      _docstamp;

        reported_t();
        ~reported_t();
    };

    //----------------------------------------------------------------
    // class holding config values
    //----------------------------------------------------------------
    class config_t
    {
    private:
        config_t(const config_t &);
        config_t& operator=(const config_t &);

    public:
        char       *_name;
        uint32_t    _unitrefcost;	      // Cost to reference us
        uint32_t    _confPartID;	      // Partid configured in partitions file
        uint32_t    _confRowID;	      // What row this engine belongs to
        bool	_confPartIDOverrides; // Ignore lower partid and use our conf value
        config_t(FastS_EngineDesc *desc);
        ~config_t();
    };

    // engine badness enum
    enum {
        BAD_NOT,
        BAD_ADMIN,
        BAD_CONFIG
    };

protected:
    stats_t     _stats;
    reported_t  _reported;
    config_t    _config;

    bool        _isUp;     // is this engine up ?
    uint32_t    _badness;  // engine badness indicator
    uint32_t    _partid;   // Partid we actually use

    // Total cost as seen by referencing objects
    std::atomic<uint32_t>  _totalrefcost;
    std::atomic<uint32_t>  _activecnt;	// Our "load" on search node

    FastS_PlainDataSet *_dataset; // dataset for this engine

    FastS_EngineBase *_nextds;    // list of engines in dataset
    FastS_EngineBase *_prevpart;  // list of engines in partition
    FastS_EngineBase *_nextpart;  // list of engines in partition

public:
    FastS_EngineBase(FastS_EngineDesc *desc, FastS_PlainDataSet *dataset);
    virtual ~FastS_EngineBase();

    // common engine methods
    //----------------------
    static time_t NoDocStamp() { return static_cast<time_t>(-1); }
    const char *GetName() const { return _config._name; }
    FastS_EngineBase *GetNextDS() const { return _nextds; }
    uint32_t GetQueueLen() const { return _reported._queueLen; }
    uint32_t GetDispatchers() const { return _reported._dispatchers; }
    FastS_PlainDataSet *GetDataSet() const { return _dataset; }
    uint32_t GetConfRowID() const { return _config._confRowID; }
    uint32_t GetPartID() const { return _partid; }

    time_t GetTimeStamp() const { return _reported._docstamp; }
    bool IsMLD() const { return _reported._mld; }

    bool IsUp() const { return _isUp; }
    bool IsRealBad() const { return (_badness > BAD_NOT); }
    bool isAdminBad() const { return _badness == BAD_ADMIN; }
    
    bool IsReady() const { return (IsUp() || IsRealBad()); }
    void SlowQuery(double limit, double secs, bool silent);
    void SlowDocsum(double limit, double secs);
    void AddCost();
    void SubCost();
    void SaveQueueLen_NoLock(uint32_t queueLen, uint32_t dispatchers);
    void SampleQueueLens();
    void UpdateSearchTime(double tnow, double elapsed, bool timedout);
    void NotifyFailure();
    void MarkBad(uint32_t badness);
    void ClearBad();
    void HandlePingResponse(uint32_t partid, time_t docstamp, bool mld,
                            uint32_t maxnodes, uint32_t nodes,
                            uint32_t maxparts, uint32_t parts,
                            PossCount activeDocs);
    void HandleLostConnection();
    void HandleNotOnline(int seconds);

    // common engine API
    //------------------
    virtual void LockEngine() = 0;
    virtual void UnlockEngine() = 0;
    virtual void Ping();
    virtual void HandleClearedBad() {}
    virtual void HandleUp() {}
    virtual void HandleDown() {}

    // typesafe "down"-cast
    //---------------------
    virtual FastS_FNET_Engine *GetFNETEngine()    { return NULL; }
    virtual FastS_RPC_Engine  *GetRPCEngine()     { return NULL; }
};

