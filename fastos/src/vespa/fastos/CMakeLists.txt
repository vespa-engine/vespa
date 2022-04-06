# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(fastos_objects OBJECT 
    SOURCES
    backtrace.c
    file.cpp
    file_rw_ops.cpp
    linux_file.cpp
    thread.cpp
    unix_file.cpp
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
