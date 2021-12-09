// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "slobrokserver.h"

namespace slobrok {

VESPA_THREAD_STACK_TAG(slobrok_server_thread);

SlobrokServer::SlobrokServer(ConfigShim &shim)
    : _env(shim),
      _thread(*this, slobrok_server_thread)
{
    _thread.start();
}

SlobrokServer::SlobrokServer(uint32_t port)
    : _env(ConfigShim(port)),
      _thread(*this, slobrok_server_thread)
{
    _thread.start();
}


SlobrokServer::~SlobrokServer()
{
    _env.shutdown();
    _thread.join();
}

void
SlobrokServer::run()
{
    _env.MainLoop();
}

} // namespace slobrok
