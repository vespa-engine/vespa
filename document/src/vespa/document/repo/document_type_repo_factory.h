// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <mutex>
#include <map>

namespace document {

namespace internal {
    class InternalDocumenttypesType;
}

class DocumentTypeRepo;

/*
 * Factory class for document type repos. Same instance is returned
 * for equal config.
 */
class DocumentTypeRepoFactory {
    using DocumenttypesConfig = const internal::InternalDocumenttypesType;
    struct DocumentTypeRepoEntry {
        std::weak_ptr<const DocumentTypeRepo> repo;
        std::unique_ptr<const DocumenttypesConfig> config;

        DocumentTypeRepoEntry(std::weak_ptr<const DocumentTypeRepo> repo_in,
                      std::unique_ptr<const DocumenttypesConfig> config_in)
            : repo(std::move(repo_in)),
              config(std::move(config_in))
        {
        }
    };
    using DocumentTypeRepoMap = std::map<const void *, DocumentTypeRepoEntry>;
    class Deleter;

    static std::mutex _mutex;
    static DocumentTypeRepoMap _repos;

    static void deleteRepo(DocumentTypeRepo *repoRawPtr) noexcept;
public:
    /*
     * Since same instance is returned for equal config, we return a shared
     * pointer to a const repo.  The repo should be considered immutable.
     */
    static std::shared_ptr<const DocumentTypeRepo> make(const DocumenttypesConfig &config);
};

}
