# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if(CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin")
  set(VESPA_EPOLL_FLAVOUR "emulated_epoll.cpp")
else()
  set(VESPA_EPOLL_FLAVOUR "native_epoll.cpp")
endif()

vespa_add_library(vespalib_vespalib_net OBJECT
    SOURCES
    async_resolver.cpp
    connection_auth_context.cpp
    crypto_engine.cpp
    crypto_socket.cpp
    selector.cpp
    server_socket.cpp
    socket.cpp
    socket_address.cpp
    socket_handle.cpp
    socket_options.cpp
    socket_spec.cpp
    socket_utils.cpp
    sync_crypto_socket.cpp
    wakeup_pipe.cpp
    ${VESPA_EPOLL_FLAVOUR}
    DEPENDS
)
