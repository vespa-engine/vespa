// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ok_state.h"
#include "cmd.h"
#include "remote_slobrok.h"

#include <deque>
#include <string>
#include <unordered_map>

namespace slobrok {

//-----------------------------------------------------------------------------

class SBEnv;
class RpcServerMap;
class RpcServerManager;

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
    using PartnerMap = std::unordered_map<std::string, std::unique_ptr<RemoteSlobrok>>;
    PartnerMap _partners;

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
        class WorkItem: public FRT_IRequestWait, public FNET_Task
        {
        private:
            WorkPackage    &_pkg;
            FRT_RPCRequest *_pendingReq;
            RemoteSlobrok  *_remslob;
        public:
            void expedite();
            void RequestDone(FRT_RPCRequest *req) override;
            void PerformTask() override;
            WorkItem(WorkPackage &pkg, RemoteSlobrok *rem, FRT_RPCRequest *req);
            WorkItem(const WorkItem&) = delete;
            WorkItem& operator= (const WorkItem&) = delete;
            ~WorkItem();
        };
        std::vector<std::unique_ptr<WorkItem>> _work;
        size_t                  _doneCnt;
        size_t                  _numDenied;
        ScriptCommand        _donehandler;
    public:
        ExchangeManager        &_exchanger;
        enum op_type { OP_NOP, OP_WANTADD, OP_DOADD, OP_REMOVE };
        op_type _optype;
        const std::string _name;
        const std::string _spec;
        void addItem(RemoteSlobrok *partner);
        void doneItem(bool denied);
        void expedite();
        WorkPackage(const WorkPackage&) = delete;
        WorkPackage& operator= (const WorkPackage&) = delete;
        WorkPackage(op_type op, const std::string & name, const std::string & spec,
                    ExchangeManager &exchanger, ScriptCommand  donehandler);
        ~WorkPackage();
    };

public:
    ExchangeManager(const ExchangeManager &) = delete;
    ExchangeManager &operator=(const ExchangeManager &) = delete;
    ExchangeManager(SBEnv &env, RpcServerMap &rpcsrvmap);
    ~ExchangeManager();

    SBEnv             &_env;
    RpcServerManager  &_rpcsrvmanager;
    RpcServerMap      &_rpcsrvmap;

    OkState addPartner(const std::string & name, const std::string & spec);
    void removePartner(const std::string & name);
    std::vector<std::string> getPartnerList();

    void forwardRemove(const std::string & name, const std::string & spec);

    void wantAdd(const std::string & name, const std::string & spec, ScriptCommand rdc);
    void doAdd(const std::string & name, const std::string & spec, ScriptCommand rdc);

    RemoteSlobrok *lookupPartner(const std::string & name) const;
    void healthCheck();
};

//-----------------------------------------------------------------------------

} // namespace slobrok
