// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankmanager.h"
#include <vespa/searchsummary/docsummary/juniperproperties.h>
#include <vespa/storage/visiting/visitor.h>
#include <vespa/config/retriever/simpleconfigurer.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/vsm/vsm/vsm-adapter.h>
#include <vespa/fastlib/text/normwordfolder.h>

namespace streaming {

class SearchEnvironment : public storage::VisitorEnvironment
{
private:
    class Env : public config::SimpleConfigurable {
    public:
        typedef std::shared_ptr<Env> SP;
        Env(const vespalib::string & muffens, const config::ConfigUri & configUri, Fast_NormalizeWordFolder & wf);
        ~Env();
        const vsm::VSMAdapter * getVSMAdapter() const { return _vsmAdapter.get(); }
        const RankManager * getRankManager() const { return _rankManager.get(); }
        void configure(const config::ConfigSnapshot & snapshot) override;

        static config::ConfigKeySet createKeySet(const vespalib::string & configId);
    private:
        const vespalib::string           _configId;
        config::SimpleConfigurer         _configurer;
        std::unique_ptr<vsm::VSMAdapter> _vsmAdapter;
        std::unique_ptr<RankManager>     _rankManager;
    };
    typedef vespalib::hash_map<vespalib::string, Env::SP> EnvMap;
    typedef std::unique_ptr<EnvMap> EnvMapUP;
    typedef std::vector<EnvMapUP> ThreadLocals;

    static __thread EnvMap * _localEnvMap;
    EnvMap                   _envMap;
    ThreadLocals             _threadLocals;
    vespalib::Lock           _lock;
    Fast_NormalizeWordFolder _wordFolder;
    config::ConfigUri        _configUri;

    Env & getEnv(const vespalib::string & searchcluster);

public:
    SearchEnvironment(const config::ConfigUri & configUri);
    ~SearchEnvironment();
    const vsm::VSMAdapter * getVSMAdapter(const vespalib::string & searchcluster) { return getEnv(searchcluster).getVSMAdapter(); }
    const RankManager * getRankManager(const vespalib::string & searchcluster)    { return getEnv(searchcluster).getRankManager(); }
};

}

