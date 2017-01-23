# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_eval_expr_app
    SOURCES
    eval_expr.cpp
    INSTALL bin
    DEPENDS
    eval
)
