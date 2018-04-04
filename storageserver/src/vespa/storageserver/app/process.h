// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::Process
 *
 * \brief Storage process as a library.
 *
 * A class with a main function cannot be tested within C++ code. This class
 * contains the process as a library such that it can be tested and used in
 * other pieces of code.
 *
 * Specializations of this class will exist to add the funcionality needed for
 * the various process types.
 */

#pragma once

#include <vespa/document/config/config-documenttypes.h>
#include <vespa/storage/storageserver/applicationgenerationfetcher.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/config/subscription/configsubscriber.h>

namespace document { class DocumentTypeRepo; }

namespace storage {

class StorageNode;
class StorageNodeContext;

class Process : public ApplicationGenerationFetcher {
protected:
    using DocumentTypeRepoSP = std::shared_ptr<const document::DocumentTypeRepo>;
    config::ConfigUri _configUri;
    DocumentTypeRepoSP getTypeRepo() { return _repos.back(); }
    config::ConfigSubscriber _configSubscriber;

private:
    config::ConfigHandle<document::DocumenttypesConfig>::UP _documentHandler;
    std::vector<DocumentTypeRepoSP> _repos;

public:
    typedef std::unique_ptr<Process> UP;

    Process(const config::ConfigUri & configUri);
    virtual ~Process() {}

    virtual void setupConfig(uint64_t subscribeTimeout);
    virtual void createNode() = 0;
    virtual bool configUpdated();
    virtual void updateConfig();

    virtual void shutdown();
    virtual void removeConfigSubscriptions() {}

    virtual StorageNode& getNode() = 0;
    virtual StorageNodeContext& getContext() = 0;

    virtual int64_t getGeneration() const override;
};

} // storage

