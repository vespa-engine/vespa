# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_eval_value_cache OBJECT
    SOURCES
    constant_value_cache.cpp
    constant_tensor_loader.cpp
    DEPENDS
)
