// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * \class storage::OpsLogger
 *
 * \brief Storage link that can be configured to log all storage operations to
 * a file.
*/
#pragma once

#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/config/config-stor-opslogger.h>
#include <vespa/config/helper/ifetchercallback.h>

namespace config {
    class ConfigUri;
    class ConfigFetcher;
}

namespace storage {

class OpsLogger : public StorageLink,
                  public config::IFetcherCallback<vespa::config::content::core::StorOpsloggerConfig> {
public:
    explicit OpsLogger(StorageComponentRegister&,
                       const config::ConfigUri & configUri);
    ~OpsLogger() override;

    void onClose() override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    bool onPutReply(const std::shared_ptr<api::PutReply>& msg) override;
    bool onUpdateReply(const std::shared_ptr<api::UpdateReply>& msg) override;
    bool onRemoveReply(const std::shared_ptr<api::RemoveReply>& msg) override;
    bool onGetReply(const std::shared_ptr<api::GetReply>& msg) override;

    /** Ignore all replies on the way down the storage chain. */
    bool onDown(const std::shared_ptr<api::StorageMessage>&) override { return false; };
    void configure(std::unique_ptr<vespa::config::content::core::StorOpsloggerConfig> config) override;
private:
    std::mutex    _lock;
    std::string   _fileName;
    FILE        * _targetFile;
    framework::Component _component;

    std::unique_ptr<config::ConfigFetcher> _configFetcher;
};

}
