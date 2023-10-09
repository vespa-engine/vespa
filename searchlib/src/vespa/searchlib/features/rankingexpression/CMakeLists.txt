# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_features_rankingexpression OBJECT
    SOURCES
    expression_replacer.cpp
    intrinsic_blueprint_adapter.cpp
    intrinsic_expression.cpp
    DEPENDS
)
