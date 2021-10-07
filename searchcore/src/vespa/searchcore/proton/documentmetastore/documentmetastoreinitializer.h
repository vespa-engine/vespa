// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentmetastore.h"
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/vespalib/stllike/string.h>

namespace proton::documentmetastore {

/*
 * Class representing an Initializer task for loading document meta store
 * from disk to memory during proton startup.
 */
class DocumentMetaStoreInitializer : public initializer::InitializerTask
{
    vespalib::string          _baseDir;
    vespalib::string          _subDbName;
    vespalib::string          _docTypeName;
    DocumentMetaStore::SP     _dms;

public:
    using SP = std::shared_ptr<DocumentMetaStoreInitializer>;

    // Note: lifetime of result must be handled by caller.
    DocumentMetaStoreInitializer(const vespalib::string baseDir,
                                 const vespalib::string &subDbName,
                                 const vespalib::string &docTypeName,
                                 DocumentMetaStore::SP dms);
    void run() override;
};


}
