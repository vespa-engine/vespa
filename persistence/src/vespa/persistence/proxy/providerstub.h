// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/closure.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/fnet/frt/invokable.h>
#include <vespa/fnet/task.h>
#include <memory>

class FRT_Supervisor;

namespace document { class DocumentTypeRepo; }

namespace storage {
namespace spi {
class PersistenceProvider;

class ProviderStub : private FRT_Invokable
{
public:
    struct PersistenceProviderFactory {
        virtual std::unique_ptr<PersistenceProvider> create() const = 0;
        virtual ~PersistenceProviderFactory() {}
    };

private:
    struct ProviderCleanupTask : FNET_Task {
        vespalib::ThreadStackExecutor &executor;
        std::unique_ptr<PersistenceProvider> &provider;
        ProviderCleanupTask(FNET_Scheduler *s,
                            vespalib::ThreadStackExecutor &e,
                            std::unique_ptr<PersistenceProvider> &p)
            : FNET_Task(s), executor(e), provider(p) {}
        void PerformTask() override {
            executor.sync();
            assert(provider.get() != 0);
            provider.reset();
        }
    };

    std::unique_ptr<FRT_Supervisor> _supervisor;
    vespalib::ThreadStackExecutor _executor;
    const document::DocumentTypeRepo *_repo;
    PersistenceProviderFactory &_factory;
    std::unique_ptr<PersistenceProvider> _provider;
    ProviderCleanupTask _providerCleanupTask;

    void HOOK_fini(FRT_RPCRequest *req);

    void detachAndRun(FRT_RPCRequest *req, vespalib::Closure::UP closure);
    void RPC_connect(FRT_RPCRequest *req);
    void RPC_initialize(FRT_RPCRequest *req);
    void RPC_getPartitionStates(FRT_RPCRequest *req);
    void RPC_listBuckets(FRT_RPCRequest *req);
    void RPC_setClusterState(FRT_RPCRequest *req);
    void RPC_setActiveState(FRT_RPCRequest *req);
    void RPC_getBucketInfo(FRT_RPCRequest *req);
    void RPC_put(FRT_RPCRequest *req);
    void RPC_removeById(FRT_RPCRequest *req);
    void RPC_removeIfFound(FRT_RPCRequest *req);
    void RPC_update(FRT_RPCRequest *req);
    void RPC_flush(FRT_RPCRequest *req);
    void RPC_get(FRT_RPCRequest *req);
    void RPC_createIterator(FRT_RPCRequest *req);
    void RPC_iterate(FRT_RPCRequest *req);
    void RPC_destroyIterator(FRT_RPCRequest *req);
    void RPC_createBucket(FRT_RPCRequest *req);
    void RPC_deleteBucket(FRT_RPCRequest *req);
    void RPC_getModifiedBuckets(FRT_RPCRequest *req);
    void RPC_split(FRT_RPCRequest *req);
    void RPC_join(FRT_RPCRequest *req);
    void RPC_move(FRT_RPCRequest *req);
    void RPC_maintain(FRT_RPCRequest *req);
    void RPC_removeEntry(FRT_RPCRequest *req);

    void SetupRpcCalls();

public:
    typedef std::unique_ptr<ProviderStub> UP;

    ProviderStub(int port, uint32_t threads,
                 const document::DocumentTypeRepo &repo,
                 PersistenceProviderFactory &factory);
    ~ProviderStub();

    bool hasClient() const { return (_provider.get() != 0); }
    int getPort() const;
    void setRepo(const document::DocumentTypeRepo &repo) {
        _repo = &repo;
    }
    void sync() { _executor.sync(); }
};

}  // namespace spi
}  // namespace storage

