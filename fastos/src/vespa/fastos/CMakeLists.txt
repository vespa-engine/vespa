# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${VTAG_DEFINES}")
vespa_add_library(fastos
    SOURCES
    app.cpp
    backtrace.c
    file.cpp
    linux_file.cpp
    serversocket.cpp
    socket.cpp
    socketevent.cpp
    thread.cpp
    time.cpp
    timestamp.cpp
    unix_app.cpp
    unix_cond.cpp
    unix_dynamiclibrary.cpp
    unix_file.cpp
    unix_ipc.cpp
    unix_mutex.cpp
    unix_process.cpp
    unix_socket.cpp
    unix_thread.cpp
    unix_time.cpp
    INSTALL lib64
    DEPENDS
    ${CMAKE_DL_LIBS}
)
find_package(Threads REQUIRED)
target_link_libraries(fastos PUBLIC ${CMAKE_THREAD_LIBS_INIT})
