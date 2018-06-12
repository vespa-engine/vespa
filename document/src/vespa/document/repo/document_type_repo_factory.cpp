// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_type_repo_factory.h"
#include "documenttyperepo.h"
#include <vespa/document/config/config-documenttypes.h>
#include <iostream>

#include <vespa/log/log.h>
LOG_SETUP(".document.repo.document_type_repo_factory");

namespace document {

std::mutex DocumentTypeRepoFactory::_mutex;
DocumentTypeRepoFactory::DocumentTypeRepoMap DocumentTypeRepoFactory::_repos;

namespace {

class EmptyFactoryCheck
{
public:
    ~EmptyFactoryCheck();
};

EmptyFactoryCheck::~EmptyFactoryCheck()
{
    if (!DocumentTypeRepoFactory::empty()) {
        std::cerr << "DocumentTypeRepoFactory not empty at shutdown" << std::endl;
        LOG_ABORT("should not be reached");
    }
}

EmptyFactoryCheck emptyFactoryCheck;

}

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
    auto repoup = std::make_unique<DocumentTypeRepo>(*repoConfig);
    auto repo = std::shared_ptr<const DocumentTypeRepo>(repoup.release(), Deleter());
    _repos.emplace(repo.get(), DocumentTypeRepoEntry(repo, std::move(repoConfig)));
    return repo;
}

bool
DocumentTypeRepoFactory::empty()
{
    std::lock_guard guard(_mutex);
    return _repos.empty();
}

}
