# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchsummary_juniper OBJECT
    SOURCES
    Matcher.cpp
    appender.cpp
    sumdesc.cpp
    mcand.cpp
    keyocc.cpp
    juniperparams.cpp
    juniper_separators.cpp
    SummaryConfig.cpp
    tokenizer.cpp
    propreader.cpp
    stringmap.cpp
    rpinterface.cpp
    dpinterface.cpp
    querynode.cpp
    queryvisitor.cpp
    queryhandle.cpp
    matchobject.cpp
    result.cpp
    config.cpp
    matchelem.cpp
    specialtokenregistry.cpp
    DEPENDS
)
