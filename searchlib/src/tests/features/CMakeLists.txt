# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_prod_features_test_app
    SOURCES
    prod_features.cpp
    prod_features_framework.cpp
    prod_features_attributematch.cpp
    prod_features_fieldmatch.cpp
    prod_features_fieldtermmatch.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_prod_features_test_app COMMAND sh prod_features_test.sh)
vespa_add_executable(searchlib_featurebenchmark_app
    SOURCES
    featurebenchmark.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_featurebenchmark_app COMMAND searchlib_featurebenchmark_app BENCHMARK)
