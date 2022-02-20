// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
struct StorageNodeContext;

class Process : public ApplicationGenerationFetcher {
protected:
    using DocumentTypeRepoSP = std::shared_ptr<const document::DocumentTypeRepo>;
    config::ConfigUri _configUri;
    DocumentTypeRepoSP getTypeRepo() { return _repos.back(); }
    config::ConfigSubscriber _configSubscriber;

private:
    config::ConfigHandle<document::config::DocumenttypesConfig>::UP _documentHandler;
    std::vector<DocumentTypeRepoSP> _repos;

public:
    using UP = std::unique_ptr<Process>;

    Process(const config::ConfigUri & configUri);
    ~Process() override;

    virtual void setupConfig(vespalib::duration subscribeTimeout);
    virtual void createNode() = 0;
    virtual bool configUpdated();
    virtual void updateConfig();

    virtual void shutdown();
    virtual void removeConfigSubscriptions() {}

    virtual StorageNode& getNode() = 0;
    virtual StorageNodeContext& getContext() = 0;

    int64_t getGeneration() const override;
};

} // storage

