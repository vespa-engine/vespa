# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# @author Vegard Sjonfjell
# @author Eirik Nygaard
# @author Arnstein Ressem
cmake_minimum_required(VERSION 3.5 FATAL_ERROR)

include(functions.cmake)
list(APPEND CMAKE_MODULE_PATH
     "$ENV{HOME}/share/cmake/Modules"
     "/opt/vespa-deps/share/cmake/Modules"
     "${CMAKE_CURRENT_SOURCE_DIR}/cmake"
)
include(default_build_settings.cmake)
vespa_detect_build_platform()
message("-- Vespa build platform is ${VESPA_OS_DISTRO} ${VESPA_OS_DISTRO_VERSION}")
vespa_use_default_cxx_compiler()
vespa_use_default_java_home()

project(vespa CXX C)
vespa_use_default_vespa_unprivileged()
vespa_use_default_cmake_install_prefix()
vespa_use_default_vespa_user()
vespa_use_default_build_settings()

# allows import of project in CLion on OSX
if (${CMAKE_SYSTEM_NAME} MATCHES "Darwin")
    set(CMAKE_THREAD_LIBS_INIT "-lpthread")
endif()

# TODO: Move this to where it's actually needed
find_package(JNI REQUIRED)

find_package(GTest REQUIRED)

include(build_settings.cmake)

# Enable CTest unit testing
enable_testing()

vespa_install_data(valgrind-suppressions.txt etc/vespa)

# Include vespa config definitions in every target
include_directories(BEFORE ${CMAKE_BINARY_DIR}/configdefinitions/src)

add_subdirectory(application-model)
add_subdirectory(application-preprocessor)
add_subdirectory(athenz-identity-provider-service)
add_subdirectory(component)
add_subdirectory(config-bundle)
add_subdirectory(config-model)
add_subdirectory(config-model-api)
add_subdirectory(config-provisioning)
add_subdirectory(config-proxy)
add_subdirectory(config)
add_subdirectory(config-model-fat)
add_subdirectory(configd)
add_subdirectory(configdefinitions)
add_subdirectory(configgen)
add_subdirectory(configserver)
add_subdirectory(configserver-flags)
add_subdirectory(configutil)
add_subdirectory(container-core)
add_subdirectory(container-core-config)
add_subdirectory(container-di)
add_subdirectory(container-disc)
add_subdirectory(container-jersey2)
add_subdirectory(container-messagebus)
add_subdirectory(container-search)
add_subdirectory(container-search-gui)
add_subdirectory(container-search-and-docproc)
add_subdirectory(cloud-tenant-cd)
add_subdirectory(clustercontroller-apps)
add_subdirectory(clustercontroller-core)
add_subdirectory(clustercontroller-reindexer)
add_subdirectory(clustercontroller-utils)
add_subdirectory(defaults)
add_subdirectory(docker-api)
add_subdirectory(docproc)
add_subdirectory(docprocs)
add_subdirectory(document)
add_subdirectory(documentapi)
add_subdirectory(eval)
add_subdirectory(fastlib)
add_subdirectory(fastos)
add_subdirectory(fbench)
add_subdirectory(fileacquirer)
add_subdirectory(filedistribution)
add_subdirectory(flags)
add_subdirectory(fnet)
add_subdirectory(fsa)
add_subdirectory(hosted-zone-api)
add_subdirectory(jdisc-cloud-aws)
add_subdirectory(jdisc_core)
add_subdirectory(jdisc-security-filters)
add_subdirectory(jdisc_http_service)
add_subdirectory(jdisc_jetty)
add_subdirectory(jrt_test)
add_subdirectory(juniper)
add_subdirectory(linguistics)
add_subdirectory(logd)
add_subdirectory(logserver)
add_subdirectory(logforwarder)
add_subdirectory(lowercasing_test)
add_subdirectory(messagebus)
add_subdirectory(messagebus_test)
add_subdirectory(metrics)
add_subdirectory(metrics-proxy)
add_subdirectory(model-evaluation)
add_subdirectory(model-integration)
add_subdirectory(node-admin)
add_subdirectory(node-repository)
add_subdirectory(orchestrator)
add_subdirectory(persistence)
add_subdirectory(persistencetypes)
add_subdirectory(predicate-search)
add_subdirectory(searchcommon)
add_subdirectory(searchcore)
add_subdirectory(searchcorespi)
add_subdirectory(searchlib)
add_subdirectory(searchsummary)
add_subdirectory(security-tools)
add_subdirectory(security-utils)
add_subdirectory(service-monitor)
add_subdirectory(simplemetrics)
add_subdirectory(slobrok)
add_subdirectory(staging_vespalib)
add_subdirectory(standalone-container)
add_subdirectory(storage)
add_subdirectory(storageapi)
add_subdirectory(storageframework)
add_subdirectory(storageserver)
add_subdirectory(statistics)
add_subdirectory(streamingvisitors)
add_subdirectory(tenant-cd-api)
add_subdirectory(vbench)
add_subdirectory(vdslib)
add_subdirectory(vdstestlib)
add_subdirectory(vespa-athenz)
add_subdirectory(vespa-http-client)
add_subdirectory(vespa-osgi-testrunner)
add_subdirectory(vespa-testrunner-components)
add_subdirectory(vespa_feed_perf)
add_subdirectory(vespa_jersey2)
add_subdirectory(vespabase)
add_subdirectory(vespaclient)
add_subdirectory(vespaclient-core)
add_subdirectory(vespaclient-container-plugin)
add_subdirectory(vespaclient-java)
add_subdirectory(vespajlib)
add_subdirectory(vespalib)
add_subdirectory(vespalog)
if(NOT CMAKE_HOST_SYSTEM_NAME STREQUAL "Darwin" AND
   NOT DEFINED VESPA_USE_SANITIZER)
  add_subdirectory(vespamalloc)
endif()
add_subdirectory(vsm)
add_subdirectory(zkfacade)
add_subdirectory(zookeeper-command-line-client)
add_subdirectory(zookeeper-server)

# Add any extra projects
add_extra_projects()

# Create module targets with name ${MODULE}+module depending on every target defined within that module
__create_module_targets(TARGETS "module")

# Create module targets with name ${MODULE}+test depending on every test target defined within that module
__create_module_targets(TEST_TARGETS "test")

# Create module targets with name ${MODULE}+source depending on every source target defined within that module
__create_module_targets(SOURCE_TARGETS "source")

# Create module targets with name ${MODULE}+object depending on every object target defined within that module
__create_module_targets(OBJECT_TARGETS "object")

# Create module source dependencies
__create_module_source_dependencies()
