# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_cblas_bench_app
    SOURCES
    cblas_bench.cpp
    DEPENDS
    vespaeval
)
vespa_add_target_external_dependency(eval_cblas_bench_app benchmark)
vespa_add_target_package_dependency(eval_cblas_bench_app BLAS)
