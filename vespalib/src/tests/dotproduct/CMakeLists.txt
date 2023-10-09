# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_dotproductbenchmark_app
    SOURCES
    dotproductbenchmark.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_dotproductbenchmark_app_sparse-ordered COMMAND vespalib_dotproductbenchmark_app 10 10 1000 1000 BENCHMARK)
vespa_add_test(NAME vespalib_dotproductbenchmark_app_sparse-unordered COMMAND vespalib_dotproductbenchmark_app 10 10 1000 1000 BENCHMARK)
vespa_add_test(NAME vespalib_dotproductbenchmark_app_full COMMAND vespalib_dotproductbenchmark_app 10 10 1000 1000 BENCHMARK) 

# benchmark: dotproductbenchmark
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark sparse-ordered   1000 1000 1000 1000
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark sparse-unordered 1000 1000 1000 1000
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark full             1000 1000 1000 1000 
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark sparse-ordered   1000 1000 100 1000
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark sparse-unordered 1000 1000 100 1000
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark full             1000 1000 100 1000 
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark sparse-ordered   1000 1000 1000 100
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark sparse-unordered 1000 1000 1000 100
# 	$(HIDE)$(LDL) time $(VALGRIND) ./dotproductbenchmark full             1000 1000 1000 100 
