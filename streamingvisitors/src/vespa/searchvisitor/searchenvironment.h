// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankmanager.h"
#include <vespa/searchsummary/docsummary/juniperproperties.h>
#include <vespa/storage/visiting/visitor.h>
#include <vespa/config/retriever/simpleconfigurer.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/vsm/vsm/vsm-adapter.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <mutex>

namespace streaming {

class SearchEnvironmentSnapshot;

class SearchEnvironment : public storage::VisitorEnvironment
{
private:
    class Env : public config::SimpleConfigurable {
    public:
        using SP = std::shared_ptr<Env>;
        Env(const config::ConfigUri& configUri, const Fast_NormalizeWordFolder& wf);
        ~Env() override;
        void configure(const config::ConfigSnapshot & snapshot) override;

        static config::ConfigKeySet createKeySet(const vespalib::string & configId);
        std::shared_ptr<const SearchEnvironmentSnapshot> get_snapshot();
    private:
        const vespalib::string           _configId;
        config::SimpleConfigurer         _configurer;
        std::unique_ptr<vsm::VSMAdapter> _vsmAdapter;
        std::unique_ptr<RankManager>     _rankManager;
        std::shared_ptr<const SearchEnvironmentSnapshot> _snapshot;
        std::mutex _lock;
    };
    using EnvMap = vespalib::hash_map<vespalib::string, Env::SP>;
    using EnvMapUP = std::unique_ptr<EnvMap>;
    using ThreadLocals = std::vector<EnvMapUP>;

    static __thread EnvMap * _localEnvMap;
    EnvMap                   _envMap;
    ThreadLocals             _threadLocals;
    std::mutex               _lock;
    Fast_NormalizeWordFolder _wordFolder;
    config::ConfigUri        _configUri;

    Env & getEnv(const vespalib::string & searchcluster);

public:
    SearchEnvironment(const config::ConfigUri & configUri);
    ~SearchEnvironment();
    std::shared_ptr<const SearchEnvironmentSnapshot> get_snapshot(const vespalib::string& search_cluster);
    // Should only be used by unit tests to simulate that the calling thread is finished.
    void clear_thread_local_env_map();
};

}

