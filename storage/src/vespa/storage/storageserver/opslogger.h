// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * \class storage::OpsLogger
 *
 * \brief Storage link that can be configured to log all storage operations to
 * a file.
*/
#pragma once

#include <vespa/storage/common/storagelink.h>
#include <vespa/storageframework/storageframework.h>

#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/config/config-stor-opslogger.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/config/config.h>

namespace storage {

class OpsLogger : public StorageLink,
                  public config::IFetcherCallback<vespa::config::content::core::StorOpsloggerConfig> {
public:
    explicit OpsLogger(StorageComponentRegister&,
                       const config::ConfigUri & configUri);
    ~OpsLogger();

    void onClose();

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

    bool onPutReply(const std::shared_ptr<api::PutReply>& msg);
    bool onUpdateReply(const std::shared_ptr<api::UpdateReply>& msg);
    bool onRemoveReply(const std::shared_ptr<api::RemoveReply>& msg);
    bool onGetReply(const std::shared_ptr<api::GetReply>& msg);

    /** Ignore all replies on the way down the storage chain. */
    bool onDown(const std::shared_ptr<api::StorageMessage>&)
        { return false; };

    void configure(std::unique_ptr<vespa::config::content::core::StorOpsloggerConfig> config);

private:
    vespalib::Lock _lock;
    std::string _fileName;
    FILE* _targetFile;
    framework::Component _component;

    config::ConfigFetcher _configFetcher;
};

}

