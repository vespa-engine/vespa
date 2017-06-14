// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/vespalib/util/referencecounter.h>
#include <vespa/searchcore/fdispatch/common/appcontext.h>
#include <vespa/searchcore/fdispatch/search/configdesc.h>

class FastS_DataSetBase;
class FastS_ISearch;

class FastS_DataSetCollection : public vespalib::ReferenceCounter
{
private:
    FastS_DataSetCollection(const FastS_DataSetCollection &);
    FastS_DataSetCollection& operator=(const FastS_DataSetCollection &);

public:
    // used by Monitor to service old query queues.
    FastS_DataSetCollection *_nextOld;

private:
    FastS_DataSetCollDesc *_configDesc;
    FastS_AppContext      *_appCtx;

    FastS_DataSetBase    **_datasets;
    uint32_t               _datasets_size;

    uint32_t               _gencnt;
    bool                   _frozen;
    bool                   _error;

    FastS_DataSetBase *CreateDataSet(FastS_DataSetDesc *desc);
    bool AddDataSet(FastS_DataSetDesc *desc);

public:
    explicit FastS_DataSetCollection(FastS_AppContext *appCtx);
    virtual ~FastS_DataSetCollection();

    /**
     * Configure this dataset collection. Note that the given config
     * description is handed over to this object when this method is
     * called. Also note that this method replaces the old methods used
     * to add datasets and engines as well as the Freeze method. In
     * other words; this method uses the given config description to
     * create a new node setup and then freezing it. Using a NULL
     * pointer for the config description is legal; it denotes the empty
     * configuration.
     *
     * @return true(ok)/false(fail)
     * @param cfgDesc configuration description
     * @param gencnt the generation of this node setup
     **/
    bool Configure(FastS_DataSetCollDesc *cfgDesc, uint32_t gencnt);

    /**
     * This method may be used to verify that this dataset collection
     * has been successfully configured. See @ref Configure.
     *
     * @return true if successfully configured
     **/
    bool IsValid() { return (_frozen && !_error); }

    FastS_DataSetCollDesc *GetConfigDesc() { return _configDesc; }

    FastS_AppContext *GetAppContext() { return _appCtx; }

    uint32_t GetMaxNumDataSets() { return _datasets_size; }

    FastS_DataSetBase *PeekDataSet(uint32_t datasetid)
    { return (datasetid < _datasets_size) ? _datasets[datasetid] : NULL; }

    uint32_t SuggestDataSet();
    FastS_DataSetBase *GetDataSet(uint32_t datasetid);
    FastS_DataSetBase *GetDataSet();

    bool AreEnginesReady();

    // create search
    FastS_ISearch *CreateSearch(uint32_t dataSetID, FastS_TimeKeeper *timeKeeper);

    // handle old query queues
    bool IsLastRef() { return (refCount() == 1); }
    void CheckQueryQueues(FastS_TimeKeeper *timeKeeper);
    void AbortQueryQueues();
};

