# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_fuzzy OBJECT
        SOURCES
        fuzzy_matcher.cpp
        levenshtein_distance.cpp
        DEPENDS
        )

