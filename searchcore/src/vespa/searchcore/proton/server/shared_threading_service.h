// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_shared_threading_service.h"
#include "shared_threading_service_config.h"
#include <vespa/vespalib/util/threadexecutor.h>
#include <vespa/vespalib/util/syncable.h>
#include <vespa/vespalib/util/clock.h>
#include <vespa/vespalib/util/invokeserviceimpl.h>
#include <memory>

namespace proton {

/**
 * Class containing the thread executors that are shared across all document dbs.
 */
class SharedThreadingService : public ISharedThreadingService {
private:
    using Registration = std::unique_ptr<vespalib::IDestructorCallback>;
    FNET_Transport                                  & _transport;
    std::unique_ptr<vespalib::SyncableThreadExecutor> _warmup;
    std::shared_ptr<vespalib::SyncableThreadExecutor> _shared;
    std::unique_ptr<vespalib::ISequencedTaskExecutor> _field_writer;
    vespalib::InvokeServiceImpl                       _invokeService;
    std::vector<Registration>                         _invokeRegistrations;
    storage::spi::BucketExecutor&                     _bucket_executor;
    vespalib::Clock                                   _clock;
public:
    SharedThreadingService(const SharedThreadingServiceConfig& cfg,
                           FNET_Transport& transport,
                           storage::spi::BucketExecutor& bucket_executor);
    ~SharedThreadingService() override;

    std::shared_ptr<vespalib::Executor> shared_raw() { return _shared; }
    void sync_all_executors();

    vespalib::ThreadExecutor& warmup() override { return *_warmup; }
    vespalib::ThreadExecutor& shared() override { return *_shared; }
    vespalib::ISequencedTaskExecutor& field_writer() override { return *_field_writer; }
    vespalib::InvokeService & invokeService() override { return _invokeService; }
    FNET_Transport & transport() override { return _transport; }
    storage::spi::BucketExecutor& bucket_executor() override { return _bucket_executor; }
    const vespalib::Clock & clock() const override { return _clock; }
};

}
