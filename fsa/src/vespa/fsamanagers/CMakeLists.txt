# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespafsamanagers
    SOURCES
    conceptnetmanager.cpp
    fsamanager.cpp
    metadatamanager.cpp
    mutex.cpp
    rwlock.cpp
    singleton.cpp
    INSTALL lib64
    DEPENDS
    vespafsa
)
find_package(Threads REQUIRED)
target_link_libraries(vespafsamanagers PUBLIC ${CMAKE_THREAD_LIBS_INIT})

install(FILES
    conceptnethandle.h
    conceptnetmanager.h
    fsahandle.h
    fsamanager.h
    metadatahandle.h
    metadatamanager.h
    mutex.h
    refcountable.h
    rwlock.h
    singleton.h
    DESTINATION include/vespa/fsamanagers)
