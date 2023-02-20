// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "sbenv.h"
#include "configshim.h"
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/runnable.h>

namespace slobrok {

class SlobrokServer : public vespalib::Runnable
{
private:
    SBEnv              _env;
    std::thread        _thread;

public:
    SlobrokServer(ConfigShim &shim);
    SlobrokServer(uint32_t port);
    ~SlobrokServer();

    void run() override;

    void stop() { _env.shutdown(); }
};

} // namespace slobrok

