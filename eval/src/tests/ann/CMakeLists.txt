# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(eval_sift_benchmark_app
    SOURCES
    sift_benchmark.cpp
    xp-annoy-nns.cpp
    xp-hnswlike-nns.cpp
    xp-lsh-nns.cpp
    DEPENDS
    vespaeval
)

vespa_add_executable(eval_gist_benchmark_app
    SOURCES
    gist_benchmark.cpp
    xp-annoy-nns.cpp
    extended-hnsw.cpp
    xp-lsh-nns.cpp
    DEPENDS
    vespaeval
)

vespa_add_executable(eval_remove_bm_app
    SOURCES
    remove-bm.cpp
    xp-annoy-nns.cpp
    xp-hnswlike-nns.cpp
    xp-lsh-nns.cpp
    DEPENDS
    vespaeval
)
