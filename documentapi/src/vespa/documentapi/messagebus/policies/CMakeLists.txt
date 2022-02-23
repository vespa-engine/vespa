# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(documentapi_documentapipolicies OBJECT
    SOURCES
    andpolicy.cpp
    asyncinitializationpolicy.cpp
    contentpolicy.cpp
    externslobrokpolicy.cpp
    documentrouteselectorpolicy.cpp
    errorpolicy.cpp
    externpolicy.cpp
    loadbalancer.cpp
    loadbalancerpolicy.cpp
    localservicepolicy.cpp
    messagetypepolicy.cpp
    mirror_with_all.cpp
    roundrobinpolicy.cpp
    subsetservicepolicy.cpp
    DEPENDS
)
vespa_generate_config(documentapi_documentapipolicies ../../../../main/resources/configdefinitions/document-protocol-policies.def)
install_config_definition(../../../../main/resources/configdefinitions/document-protocol-policies.def documentapi.messagebus.protocol.document-protocol-policies.def)
vespa_generate_config(documentapi_documentapipolicies ../../../../main/resources/configdefinitions/documentrouteselectorpolicy.def)
install_config_definition(../../../../main/resources/configdefinitions/documentrouteselectorpolicy.def documentapi.messagebus.protocol.documentrouteselectorpolicy.def)
