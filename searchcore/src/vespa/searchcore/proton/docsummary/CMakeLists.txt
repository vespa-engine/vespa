# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_docsummary STATIC
    SOURCES
    docsumcontext.cpp
    document_store_explorer.cpp
    documentstoreadapter.cpp
    summarycompacttarget.cpp
    summaryflushtarget.cpp
    summarymanager.cpp
    summarymanagerinitializer.cpp
    DEPENDS
    searchcore_fconfig
)
