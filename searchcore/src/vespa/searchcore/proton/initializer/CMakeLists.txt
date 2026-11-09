# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_initializer STATIC
    INSTALL lib64
    OUTPUT_NAME vespa_searchcore_bucketdb
    SOURCES
    initializer_task.cpp
    task_runner.cpp
    DEPENDS
)
