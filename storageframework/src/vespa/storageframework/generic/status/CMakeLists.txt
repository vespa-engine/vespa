# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageframework_status OBJECT
    SOURCES
    statusreporter.cpp
    htmlstatusreporter.cpp
    xmlstatusreporter.cpp
    httpurlpath.cpp
    DEPENDS
)
