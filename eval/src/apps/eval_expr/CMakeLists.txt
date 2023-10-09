# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_eval_expr_app
    SOURCES
    eval_expr.cpp
    OUTPUT_NAME vespa-eval-expr
    INSTALL bin
    DEPENDS
    vespaeval
    EXTERNAL_DEPENDS
    edit
)
