// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_type_repo_factory.h"
#include "documenttyperepo.h"
#include <vespa/document/config/config-documenttypes.h>
#include <functional>

namespace document {

std::mutex DocumentTypeRepoFactory::_mutex;
DocumentTypeRepoFactory::DocumentTypeRepoMap DocumentTypeRepoFactory::_repos;

/*
 * Class handling deletion of document type repo after last reference is gone.
 */
class DocumentTypeRepoFactory::Deleter
{
public:
    void operator()(DocumentTypeRepo *repoRawPtr) const noexcept {
        deleteRepo(repoRawPtr);
    }
};

void
DocumentTypeRepoFactory::deleteRepo(DocumentTypeRepo *repoRawPtr) noexcept
{
    std::unique_ptr<const DocumentTypeRepo> repo(repoRawPtr);
    std::lock_guard guard(_mutex);
    _repos.erase(repo.get());
}

std::shared_ptr<const DocumentTypeRepo>
DocumentTypeRepoFactory::make(const DocumenttypesConfig &config)
{
    std::lock_guard guard(_mutex);
    // Return existing instance if config matches
    for (const auto &entry : _repos) {
        const auto repo = entry.second.repo.lock();
        const auto &repoConfig = *entry.second.config;
        if (repo && repoConfig == config) {
            return repo;
        }
    }
    auto repoConfig = std::make_unique<const DocumenttypesConfig>(config);
    auto repoup1 = std::make_unique<DocumentTypeRepo>(*repoConfig);
    auto repoup2 = std::unique_ptr<DocumentTypeRepo, Deleter>(repoup1.release(), Deleter());
    auto repo = std::shared_ptr<const DocumentTypeRepo>(std::move(repoup2));
    _repos.emplace(repo.get(), DocumentTypeRepoEntry(repo, std::move(repoConfig)));
    return repo;
}

}
