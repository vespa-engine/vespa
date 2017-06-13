// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/vespalib/data/slime/slime.h>

using vespalib::Slime;
using vespalib::eval::TensorSpec;

TEST("require that a tensor spec can be converted to and from slime") {
    TensorSpec spec("tensor(x[2],y{})");
    spec.add({{"x", 0}, {"y", "xxx"}}, 1.0)
        .add({{"x", 0}, {"y", "yyy"}}, 2.0)
        .add({{"x", 1}, {"y", "xxx"}}, 3.0)
        .add({{"x", 1}, {"y", "yyy"}}, 4.0);
    Slime slime;
    spec.to_slime(slime.setObject());
    fprintf(stderr, "tensor spec as slime: \n%s\n", slime.get().toString().c_str());
    EXPECT_EQUAL(TensorSpec::from_slime(slime.get()), spec);
}

TEST_MAIN() { TEST_RUN_ALL(); }
