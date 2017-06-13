# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# @author Vegard Sjonfjell
# @author Eirik Nygaard
# @author Arnstein Ressem
cmake_minimum_required(VERSION 3.5 FATAL_ERROR)

project(vespa CXX C)

# TODO: Move this to where it's actually needed
find_package(JNI REQUIRED)

include(functions.cmake)
include(build_settings.cmake)
include(install_java.cmake)

# Enable CTest unit testing
enable_testing()

# Include vespa config definitions in every target
include_directories(BEFORE ${CMAKE_BINARY_DIR}/configdefinitions/src)

add_subdirectory(config)
add_subdirectory(configd)
add_subdirectory(configdefinitions)
add_subdirectory(configserver)
add_subdirectory(configutil)
add_subdirectory(defaults)
add_subdirectory(document)
add_subdirectory(documentapi)
add_subdirectory(eval)
add_subdirectory(fastlib)
add_subdirectory(fastos)
add_subdirectory(fbench)
add_subdirectory(fileacquirer)
add_subdirectory(filedistribution)
add_subdirectory(fnet)
add_subdirectory(frtstream)
add_subdirectory(fsa)
add_subdirectory(jrt_test)
add_subdirectory(juniper)
add_subdirectory(logd)
add_subdirectory(lowercasing_test)
add_subdirectory(memfilepersistence)
add_subdirectory(messagebus)
add_subdirectory(messagebus_test)
add_subdirectory(metrics)
add_subdirectory(persistence)
add_subdirectory(persistencetypes)
add_subdirectory(searchcommon)
add_subdirectory(searchcore)
add_subdirectory(searchcorespi)
add_subdirectory(searchlib)
add_subdirectory(searchsummary)
add_subdirectory(slobrok)
add_subdirectory(staging_vespalib)
add_subdirectory(storage)
add_subdirectory(storageapi)
add_subdirectory(storageframework)
add_subdirectory(storageserver)
add_subdirectory(streamingvisitors)
add_subdirectory(vbench)
add_subdirectory(vdslib)
add_subdirectory(vdstestlib)
add_subdirectory(vespabase)
add_subdirectory(vespaclient)
add_subdirectory(vespalib)
add_subdirectory(vespalog)
add_subdirectory(vespamalloc)
add_subdirectory(vsm)

# Create module targets with name ${MODULE}+module depending on every target defined within that module
__create_module_targets(TARGETS "module")

# Create module targets with name ${MODULE}+test depending on every test target defined within that module
__create_module_targets(TEST_TARGETS "test")

# Post installation steps. Need to be the last install(...) call in this file.
install(CODE "execute_process(COMMAND ${CMAKE_CURRENT_LIST_DIR}/dist/post_install.sh ${CMAKE_INSTALL_PREFIX})")
