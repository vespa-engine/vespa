// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "test_kit.h"

#define TEST_INIT(name) do { TEST_MASTER.init(name); } while(false)
#define TEST_DONE() do { return TEST_MASTER.fini() ? 0 : 1; } while(false)

#define TEST_APPHOOK(app) \
  int main(int argc, char **argv) \
  { \
    app myapp; \
    return myapp.Entry(argc, argv); \
  }
#define TEST_SETUP(test) \
  class test : public vespalib::TestApp \
  { \
    public: int Main() override; \
  }; \
  TEST_APPHOOK(test)

namespace vespalib {

class TestApp
{
public:
    int _argc = 0;
    char **_argv = nullptr;

    virtual int Main() = 0;
    int Entry(int argc, char **argv);
    virtual ~TestApp();
};

} // namespace vespalib
