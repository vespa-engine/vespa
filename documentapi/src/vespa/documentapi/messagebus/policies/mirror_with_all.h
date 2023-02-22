// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

class FNET_Transport;
class FRT_Supervisor;
namespace slobrok { class ConfiguratorFactory; }
namespace slobrok::api { class IMirrorAPI; }

namespace documentapi {

class MirrorAndStuff {
public:
    MirrorAndStuff(const slobrok::ConfiguratorFactory & config);
    ~MirrorAndStuff();
    slobrok::api::IMirrorAPI * mirror() { return _mirror.get(); }
private:
    std::unique_ptr<FNET_Transport>          _transport;
    std::unique_ptr<FRT_Supervisor>          _orb;
    std::unique_ptr<slobrok::api::IMirrorAPI> _mirror;
};

}
