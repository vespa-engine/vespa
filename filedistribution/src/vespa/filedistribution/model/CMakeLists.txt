# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(filedistribution_filedbmodel OBJECT
    SOURCES
    zkfiledbmodel.cpp
    zkfacade.cpp
    deployedfilestodownload.cpp
    DEPENDS
)
vespa_add_library(filedistribution_filedistributionmodel STATIC
    SOURCES
    deployedfilestodownload.cpp
    filedistributionmodelimpl.cpp
    zkfacade.cpp
    zkfiledbmodel.cpp
    DEPENDS
)
vespa_generate_config(filedistribution_filedistributionmodel filereferences.def)

vespa_add_target_external_dependency(filedistribution_filedistributionmodel zookeeper_mt)
install(FILES filereferences.def DESTINATION var/db/vespa/config_server/serverdb/classes)
