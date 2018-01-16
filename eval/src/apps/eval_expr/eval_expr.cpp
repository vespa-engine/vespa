// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/tensor_spec.h>


using namespace vespalib::eval;

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <expr>\n", argv[0]);
        fprintf(stderr, "  the expression must be self-contained (no arguments)\n");
        fprintf(stderr, "  quote the expression to make it a single parameter\n");
        return 1;
    }
    Function function = Function::parse({}, argv[1]);
    if (function.has_error()) {
        fprintf(stderr, "expression error: %s\n", function.get_error().c_str());
        return 1;
    }
    InterpretedFunction interpreted(SimpleTensorEngine::ref(), function, NodeTypes());
    InterpretedFunction::Context ctx(interpreted);
    SimpleParams params({});
    const Value &result = interpreted.eval(ctx, params);
    if (result.is_double()) {
        fprintf(stdout, "%.32g\n", result.as_double());
    } else if (result.is_tensor()) {
        vespalib::string str = SimpleTensorEngine::ref().to_spec(result).to_string();
        fprintf(stdout, "%s\n", str.c_str());
    } else {
        fprintf(stdout, "error\n");
    }
    return 0;
}
