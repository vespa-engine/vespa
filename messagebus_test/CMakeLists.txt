# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
  DEPENDS
  slobrok_slobrokserver
  messagebus

  APPS
  src/binref

  TESTS
  src/tests/compile-cpp
  src/tests/compile-java
  src/tests/error
  src/tests/errorcodes
  src/tests/speed
  src/tests/trace
)

