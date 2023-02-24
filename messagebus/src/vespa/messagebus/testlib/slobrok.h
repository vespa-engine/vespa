// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/common.h>
#include <vespa/slobrok/cfg.h>
#include <thread>

namespace slobrok {
class SBEnv;
} // namespace slobrok

namespace mbus {

class Slobrok
{
private:
    std::unique_ptr<slobrok::SBEnv>  _env;
    int _port;
    std::thread _thread;

    Slobrok(const Slobrok &);
    Slobrok &operator=(const Slobrok &);

    void init();

public:
    using UP = std::unique_ptr<Slobrok>;
    Slobrok();
    Slobrok(int port);
    ~Slobrok();
    int port() const;
    config::ConfigUri config() const;
};

} // namespace mbus
