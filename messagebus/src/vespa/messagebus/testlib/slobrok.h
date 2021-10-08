// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/common.h>
#include <vespa/slobrok/cfg.h>
#include <vespa/fastos/thread.h>

namespace slobrok {
class SBEnv;
} // namespace slobrok

namespace mbus {

class Slobrok
{
private:
    class Thread : public FastOS_Runnable {
    private:
        slobrok::SBEnv *_env;
    public:
        void setEnv(slobrok::SBEnv *env);
        void Run(FastOS_ThreadInterface *, void *) override;
    };
    FastOS_ThreadPool  _pool;
    std::unique_ptr<slobrok::SBEnv>  _env;
    int                _port;
    Thread             _thread;

    Slobrok(const Slobrok &);
    Slobrok &operator=(const Slobrok &);

    void init();

public:
    typedef std::unique_ptr<Slobrok> UP;
    Slobrok();
    Slobrok(int port);
    ~Slobrok();
    int port() const;
    config::ConfigUri config() const;
};

} // namespace mbus

