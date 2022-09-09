// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "executor_thread_service.h"
#include "i_proton_configurer.h"
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/vespalib/net/http/simple_component_config_producer.h>
#include <vespa/vespalib/util/monitored_refcount.h>
#include <map>
#include <mutex>

namespace proton {

class DocumentDBDirectoryHolder;
class IDocumentDBConfigOwner;
class IProtonConfigurerOwner;
class BootstrapConfig;
class IProtonDiskLayout;

/*
 * Class to handle config changes to proton using config snapshots spanning
 * all document types.
 */
class ProtonConfigurer : public IProtonConfigurer
{
    using DocumentDBs = std::map<DocTypeName, std::pair<std::weak_ptr<IDocumentDBConfigOwner>, std::weak_ptr<DocumentDBDirectoryHolder>>>;
    using InitializeThreads = std::shared_ptr<vespalib::ThreadExecutor>;
    class ReconfigureTask;

    ExecutorThreadService                     _executor;
    IProtonConfigurerOwner                   &_owner;
    DocumentDBs                               _documentDBs;
    std::shared_ptr<ProtonConfigSnapshot>     _pendingConfigSnapshot;
    std::shared_ptr<ProtonConfigSnapshot>     _activeConfigSnapshot;
    mutable std::mutex                        _mutex;
    bool                                      _allowReconfig;
    vespalib::SimpleComponentConfigProducer   _componentConfig;
    const std::unique_ptr<IProtonDiskLayout> &_diskLayout;
    vespalib::MonitoredRefCount               _pendingReconfigureTasks;

    void performReconfigure();
    bool skipConfig(const ProtonConfigSnapshot *configSnapshot, bool initialConfig);
    void applyConfig(std::shared_ptr<ProtonConfigSnapshot> configSnapshot,
                     InitializeThreads initializeThreads, bool initialConfig);
    void configureDocumentDB(const ProtonConfigSnapshot &configSnapshot,
                             const DocTypeName &docTypeName, document::BucketSpace bucketSpace,
                             const vespalib::string &configId, InitializeThreads initializeThreads);
    void pruneDocumentDBs(const ProtonConfigSnapshot &configSnapshot);
    void pruneInitialDocumentDBDirs(const ProtonConfigSnapshot &configSnapshot);

public:
    ProtonConfigurer(vespalib::ThreadExecutor &executor,
                     IProtonConfigurerOwner &owner,
                     const std::unique_ptr<IProtonDiskLayout> &diskLayout);

    ~ProtonConfigurer() override;

    void setAllowReconfig(bool allowReconfig);

    std::shared_ptr<ProtonConfigSnapshot> getPendingConfigSnapshot();

    std::shared_ptr<ProtonConfigSnapshot> getActiveConfigSnapshot() const;

    void reconfigure(std::shared_ptr<ProtonConfigSnapshot> configSnapshot) override;

    void applyInitialConfig(InitializeThreads initializeThreads);
    vespalib::SimpleComponentConfigProducer &getComponentConfig() { return _componentConfig; }
};

} // namespace proton
