// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ok_state.h"
#include "remote_slobrok.h"

#include <deque>
#include <string>
#include <unordered_map>
#include <vespa/vespalib/util/time.h>

namespace slobrok {

//-----------------------------------------------------------------------------

class SBEnv;

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
        class WorkItem: public FRT_IRequestWait
        {
        private:
            WorkPackage    &_pkg;
            FRT_RPCRequest *_pendingReq;
            RemoteSlobrok  *_remslob;
        public:
            void expedite();
            void RequestDone(FRT_RPCRequest *req) override;
            WorkItem(WorkPackage &pkg, RemoteSlobrok *rem, FRT_RPCRequest *req);
            WorkItem(const WorkItem&) = delete;
            WorkItem& operator= (const WorkItem&) = delete;
            ~WorkItem();
        };
        std::vector<std::unique_ptr<WorkItem>> _work;
        size_t        _doneCnt;
        size_t        _numDenied;
    public:
        ExchangeManager &_exchanger;
        enum op_type { OP_REMOVE };
        const ServiceMapping _mapping;
        const op_type _optype;
        void addItem(RemoteSlobrok *partner);
        void doneItem(bool denied);
        void expedite();
        WorkPackage(const WorkPackage&) = delete;
        WorkPackage& operator= (const WorkPackage&) = delete;
        WorkPackage(op_type op, const ServiceMapping &mapping, ExchangeManager &exchanger);
        ~WorkPackage();
    };

    SBEnv             &_env;
    vespalib::steady_time _lastFullConsensusTime;

    vespalib::string diffLists(const ServiceMappingList &lhs, const ServiceMappingList &rhs);

public:
    ExchangeManager(const ExchangeManager &) = delete;
    ExchangeManager &operator=(const ExchangeManager &) = delete;
    ExchangeManager(SBEnv &env);
    ~ExchangeManager();

    SBEnv             &env() { return _env; }

    OkState addPartner(const std::string & spec);
    void removePartner(const std::string & spec);
    std::vector<std::string> getPartnerList();

    void forwardRemove(const std::string & name, const std::string & spec);

    RemoteSlobrok *lookupPartner(const std::string & name) const;
    void healthCheck();
};

//-----------------------------------------------------------------------------

} // namespace slobrok
