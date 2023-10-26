# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(config_common OBJECT
    SOURCES
    configmanager.cpp
    misc.cpp
    configparser.cpp
    errorcode.cpp
    timingvalues.cpp
    configupdate.cpp
    configholder.cpp
    configcontext.cpp
    configkey.cpp
    configsystem.cpp
    configvalue.cpp
    trace.cpp
    payload_converter.cpp
    configdefinition.cpp
    compressiontype.cpp
    vespa_version.cpp
    exceptions.cpp
    DEPENDS
)
