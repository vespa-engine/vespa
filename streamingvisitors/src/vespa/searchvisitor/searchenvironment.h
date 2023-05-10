// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankmanager.h"
#include <vespa/eval/eval/value_cache/constant_tensor_loader.h>
#include <vespa/eval/eval/value_cache/constant_value_cache.h>
#include <vespa/searchsummary/docsummary/juniperproperties.h>
#include <vespa/storage/visiting/visitor.h>
#include <vespa/config/retriever/simpleconfigurer.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/vsm/vsm/vsm-adapter.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <mutex>

class FNET_Transport;

namespace search::fef {

struct IRankingAssetsRepo;
class OnnxModels;
class RankingAssetsBuilder;
class RankingConstants;
class RankingExpressions;

}

namespace streaming {

class SearchEnvironmentSnapshot;

class SearchEnvironment : public storage::VisitorEnvironment
{
private:
    class Env : public config::SimpleConfigurable {
    public:
        using SP = std::shared_ptr<Env>;
        Env(const config::ConfigUri& configUri, const Fast_NormalizeWordFolder& wf, FNET_Transport* transport, const vespalib::string& file_distributor_connection_spec);
        ~Env() override;
        void configure(const config::ConfigSnapshot & snapshot) override;

        static config::ConfigKeySet createKeySet(const vespalib::string & configId);
        std::shared_ptr<const SearchEnvironmentSnapshot> get_snapshot();
    private:
        template <typename ConfigType, typename RankingAssetType>
        void configure_ranking_asset(std::shared_ptr<const RankingAssetType> &ranking_asset,
                                     const config::ConfigSnapshot& snapshot,
                                     search::fef::RankingAssetsBuilder& builder);
        const vespalib::string                                 _configId;
        config::SimpleConfigurer                               _configurer;
        std::unique_ptr<vsm::VSMAdapter>                       _vsmAdapter;
        std::unique_ptr<RankManager>                           _rankManager;
        std::shared_ptr<const SearchEnvironmentSnapshot>       _snapshot;
        std::mutex                                             _lock;
        vespalib::eval::ConstantTensorLoader                   _tensor_loader;
        vespalib::eval::ConstantValueCache                     _constant_value_cache;
        uint64_t                                               _generation;
        std::shared_ptr<const search::fef::OnnxModels>         _onnx_models;
        std::shared_ptr<const search::fef::RankingConstants>   _ranking_constants;
        std::shared_ptr<const search::fef::RankingExpressions> _ranking_expressions;
        std::shared_ptr<const search::fef::IRankingAssetsRepo> _ranking_assets_repo;
        FNET_Transport* const                                  _transport;
        const vespalib::string                                 _file_distributor_connection_spec;
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
    FNET_Transport* const    _transport;
    vespalib::string         _file_distributor_connection_spec;

    Env & getEnv(const vespalib::string & searchcluster);

public:
    SearchEnvironment(const config::ConfigUri & configUri, FNET_Transport* transport, const vespalib::string& file_distributor_connection_spec);
    ~SearchEnvironment();
    std::shared_ptr<const SearchEnvironmentSnapshot> get_snapshot(const vespalib::string& search_cluster);
    // Should only be used by unit tests to simulate that the calling thread is finished.
    void clear_thread_local_env_map();
};

}

