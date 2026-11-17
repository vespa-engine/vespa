// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/searchcore/proton/common/doc_type_and_subdb_name.h>
#include <vespa/searchcore/proton/initializer/initializer_task.h>

#include <string>

namespace proton {
class DocumentMetaStore;
}
namespace proton::documentmetastore {

/*
 * Class representing an Initializer task for loading document meta store
 * from disk to memory during proton startup.
 */
class DocumentMetaStoreInitializer : public initializer::InitializerTask {
    std::string                                      _baseDir;
    const std::shared_ptr<const DocTypeAndSubDbName> _doc_type_and_subdb_name;
    std::shared_ptr<DocumentMetaStore>               _dms;

public:
    using SP = std::shared_ptr<DocumentMetaStoreInitializer>;

    // Note: lifetime of result must be handled by caller.
    DocumentMetaStoreInitializer(const std::string                          baseDir,
                                 std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdbb_name_in,
                                 std::shared_ptr<DocumentMetaStore>         dms);
    void run() override;
};

} // namespace proton::documentmetastore
