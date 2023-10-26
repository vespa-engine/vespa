# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(messagebus
    SOURCES
    blob.cpp
    blobref.cpp
    callstack.cpp
    configagent.cpp
    destinationsession.cpp
    destinationsessionparams.cpp
    dynamicthrottlepolicy.cpp
    emptyreply.cpp
    error.cpp
    errorcode.cpp
    intermediatesession.cpp
    intermediatesessionparams.cpp
    message.cpp
    messagebus.cpp
    messagebusparams.cpp
    messenger.cpp
    protocolrepository.cpp
    protocolset.cpp
    reply.cpp
    replygate.cpp
    result.cpp
    routable.cpp
    routablequeue.cpp
    rpcmessagebus.cpp
    sendproxy.cpp
    sequencer.cpp
    sourcesession.cpp
    sourcesessionparams.cpp
    staticthrottlepolicy.cpp
    steadytimer.cpp
    $<TARGET_OBJECTS:messagebus_routing>
    $<TARGET_OBJECTS:messagebus_network>
    INSTALL lib64
    DEPENDS
)
vespa_generate_config(messagebus ../../main/config/messagebus.def)
install_config_definition(../../main/config/messagebus.def messagebus.messagebus.def)
