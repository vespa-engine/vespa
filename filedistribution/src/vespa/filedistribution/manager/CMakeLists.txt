# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(filedistribution_filedistributionmanager
    SOURCES
    createtorrent.cpp
    filedb.cpp
    stderr_logfwd.cpp
    $<TARGET_OBJECTS:filedistribution_filedbmodel>
    $<TARGET_OBJECTS:filedistribution_exceptionrethrower>
    INSTALL lib64
    OUTPUT_NAME filedistributionmanager
    DEPENDS
    boost_system${VESPA_BOOST_LIB_SUFFIX}
    boost_thread${VESPA_BOOST_LIB_SUFFIX}
    boost_filesystem${VESPA_BOOST_LIB_SUFFIX}
    zookeeper_mt
    ${JAVA_JVM_LIBRARY}
)
