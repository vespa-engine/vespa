# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(documentapi_documentapipolicies OBJECT
    SOURCES
    andpolicy.cpp
    externslobrokpolicy.cpp
    storagepolicy.cpp
    contentpolicy.cpp
    messagetypepolicy.cpp
    documentrouteselectorpolicy.cpp
    errorpolicy.cpp
    externpolicy.cpp
    localservicepolicy.cpp
    roundrobinpolicy.cpp
    searchcolumnpolicy.cpp
    searchrowpolicy.cpp
    subsetservicepolicy.cpp
    loadbalancer.cpp
    loadbalancerpolicy.cpp
    asyncinitializationpolicy.cpp
    DEPENDS
)
vespa_generate_config(documentapi_documentapipolicies ../../../../main/resources/configdefinitions/documentrouteselectorpolicy.def)
install(FILES ../../../../main/resources/configdefinitions/documentrouteselectorpolicy.def DESTINATION var/db/vespa/config_server/serverdb/classes)
