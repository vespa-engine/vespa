// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <deque>
#include <string>

#include <vespa/fnet/frt/frt.h>

#include <vespa/vespalib/util/hashmap.h>
#include "ok_state.h"
#include "cmd.h"
#include "remote_slobrok.h"

namespace slobrok {

//-----------------------------------------------------------------------------

class SBEnv;
class RpcServerMap;
class RpcServerManager;

using vespalib::HashMap;

//-----------------------------------------------------------------------------

/**
 * @class ExchangeManager
 * @brief Keeps track of and talks to all remote location brokers
 *
 * Handles a collection of RemoteSlobrok objects; contains classes and
 * methods for operating on all remote slobroks in parallel.
 **/
class ExchangeManager
{
private:
    ExchangeManager(const ExchangeManager &);            // Not used
    ExchangeManager &operator=(const ExchangeManager &); // Not used

    HashMap<RemoteSlobrok *> _partners;

    class WorkPackage;

    class IWorkPkgWait
    {
    public:
        virtual void donePackage(WorkPackage *pkg, bool somedenied) = 0;
        virtual ~IWorkPkgWait() {}
    };

    class WorkPackage
    {
    private:
        WorkPackage(const WorkPackage&); // not used
        WorkPackage& operator= (const WorkPackage&); // not used

        class WorkItem: public FRT_IRequestWait
        {
        private:
            WorkPackage    &_pkg;
            FRT_RPCRequest *_pendingReq;
            RemoteSlobrok  *_remslob;

            WorkItem(const WorkItem&); // not used
            WorkItem& operator= (const WorkItem&); // not used
        public:
            void expedite();
            void RequestDone(FRT_RPCRequest *req) override;
            WorkItem(WorkPackage &pkg,
                     RemoteSlobrok *rem,
                     FRT_RPCRequest *req);
            ~WorkItem();
        };
        std::vector<WorkItem *> _work;
        size_t                  _doneCnt;
        size_t                  _numDenied;
        RegRpcSrvCommand        _donehandler;
    public:
        ExchangeManager        &_exchanger;
        enum op_type { OP_NOP, OP_WANTADD, OP_DOADD, OP_REMOVE };
        op_type _optype;
        const std::string _name;
        const std::string _spec;
        void addItem(RemoteSlobrok *partner);
        void doneItem(bool denied);
        void expedite();
        WorkPackage(op_type op,
                    const char *name, const char *spec,
                    ExchangeManager &exchanger,
                    RegRpcSrvCommand  donehandler);
        ~WorkPackage();
    };

public:
    ExchangeManager(SBEnv &env, RpcServerMap &rpcsrvmap);
    ~ExchangeManager() {}

    SBEnv             &_env;
    RpcServerManager  &_rpcsrvmanager;
    RpcServerMap      &_rpcsrvmap;

    OkState addPartner(const char *name, const char *spec);
    void removePartner(const char *name);
    std::vector<std::string> getPartnerList();

    void registerFrom(RemoteSlobrok *partner);
    void reregisterTo(RemoteSlobrok *partner);

    void forwardRemove(const char *name, const char *spec);

    void wantAdd(const char *name, const char *spec, RegRpcSrvCommand rdc);
    void doAdd(const char *name, const char *spec, RegRpcSrvCommand rdc);

    RemoteSlobrok *lookupPartner(const char *name) const {
        return _partners[name];
    }

    void healthCheck();
};

//-----------------------------------------------------------------------------

} // namespace slobrok

