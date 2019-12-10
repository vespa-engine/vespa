# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fastos_objects OBJECT 
    SOURCES
    app.cpp
    backtrace.c
    file.cpp
    linux_file.cpp
    process.cpp
    thread.cpp
    unix_app.cpp
    unix_dynamiclibrary.cpp
    unix_file.cpp
    unix_ipc.cpp
    unix_process.cpp
    unix_thread.cpp
)
  
vespa_add_library(fastos
    SOURCES
    $<TARGET_OBJECTS:fastos_objects>
    INSTALL lib64
    DEPENDS
    ${CMAKE_DL_LIBS}
)
  
find_package(Threads REQUIRED)
target_link_libraries(fastos PUBLIC ${CMAKE_THREAD_LIBS_INIT})
