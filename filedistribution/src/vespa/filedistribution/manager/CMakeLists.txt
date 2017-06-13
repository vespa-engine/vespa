# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(filedistribution_filedistributionmanager
    SOURCES
    ${CMAKE_CURRENT_BINARY_DIR}/com_yahoo_vespa_filedistribution_FileDistributionManager.h
    createtorrent.cpp
    filedb.cpp
    filedistributionmanager.cpp
    stderr_logfwd.cpp
    $<TARGET_OBJECTS:filedistribution_filedbmodel>
    $<TARGET_OBJECTS:filedistribution_exceptionrethrower>
    INSTALL lib64
    OUTPUT_NAME filedistributionmanager
    DEPENDS
    boost_system-mt-d
    boost_thread-mt-d
    boost_filesystem-mt-d
    zookeeper_mt
    ${JAVA_JVM_LIBRARY}
)
target_include_directories(filedistribution_filedistributionmanager PUBLIC SYSTEM ${JNI_INCLUDE_DIRS})
add_custom_command(
    OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/com_yahoo_vespa_filedistribution_FileDistributionManager.h
    COMMAND javah -classpath ${PROJECT_SOURCE_DIR}/filedistributionmanager/target/filedistributionmanager.jar com.yahoo.vespa.filedistribution.FileDistributionManager
    MAIN_DEPENDENCY ${PROJECT_SOURCE_DIR}/filedistributionmanager/target/filedistributionmanager.jar
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
)

