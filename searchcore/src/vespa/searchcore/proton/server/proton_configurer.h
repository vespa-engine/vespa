// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_proton_configurer.h"
#include <vespa/searchcore/proton/common/doctypename.h>
#include <map>
#include <mutex>
#include "executor_thread_service.h"

namespace proton {

class IDocumentDBConfigOwner;
class IProtonConfigurerOwner;
class BootstrapConfig;

/*
 * Class to handle config changes to proton using config snapshots spanning
 * all document types.
 */
class ProtonConfigurer : public IProtonConfigurer
{
    using DocumentDBs = std::map<DocTypeName, IDocumentDBConfigOwner *>;
    using InitializeThreads = std::shared_ptr<vespalib::ThreadStackExecutorBase>;

    ExecutorThreadService _executor;
    IProtonConfigurerOwner &_owner;
    DocumentDBs _documentDBs;
    std::shared_ptr<ProtonConfigSnapshot> _pendingConfigSnapshot;
    std::shared_ptr<ProtonConfigSnapshot> _activeConfigSnapshot;
    mutable std::mutex _mutex;
    bool _allowReconfig;

    void performReconfigure();
    bool skipConfig(const ProtonConfigSnapshot *configSnapshot, bool initialConfig);
    void applyConfig(std::shared_ptr<ProtonConfigSnapshot> configSnapshot,
                     InitializeThreads initializeThreads, bool initialConfig);
    void configureDocumentDB(const ProtonConfigSnapshot &configSnapshot, const DocTypeName &docTypeName, const vespalib::string &configId, const InitializeThreads &initializeThreads);
    void pruneDocumentDBs(const ProtonConfigSnapshot &configSnapshot);
public:
    ProtonConfigurer(vespalib::ThreadStackExecutorBase &executor,
                     IProtonConfigurerOwner &owner);

    ~ProtonConfigurer();

    void setAllowReconfig(bool allowReconfig);

    std::shared_ptr<ProtonConfigSnapshot> getPendingConfigSnapshot();

    std::shared_ptr<ProtonConfigSnapshot> getActiveConfigSnapshot() const;

    virtual void reconfigure(std::shared_ptr<ProtonConfigSnapshot> configSnapshot) override;

    void applyInitialConfig(InitializeThreads initializeThreads);
};

} // namespace proton
