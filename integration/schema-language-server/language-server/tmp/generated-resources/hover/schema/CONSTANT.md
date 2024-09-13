## constant

*Prefer to define constants in the rank profiles that need them, with rank profile inheritance to avoid repetition. See [constants](https://docs.vespa.ai/en/reference/schema-reference.html#constants).*

Contained in [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema). This defines a named constant tensor located in a file with a given type that can be used in ranking expressions using the rank feature [constant(name)](https://docs.vespa.ai/en/reference/rank-features.html#constant(name)):

```
constant [name] {
    [body]
}
```

The body of a constant must contain:

| Name |                                                                                                                                                       Description                                                                                                                                                       | Occurrence |
|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| file | Path to the file containing this constant, relative from the application package root. The file must be stored in a valid [tensor JSON Format](https://docs.vespa.ai/en/reference/constant-tensor-json-format.html) and end with `.json`. The file may be lz4 compressed, in which case the ending must be `.json.lz4`. | One        |
| type | The type of the constant tensor, refer to [tensor-type-spec](https://docs.vespa.ai/en/reference/tensor.html#tensor-type-spec) for reference.                                                                                                                                                                            | One        |

Constant tensor example:

```
constant my_constant_tensor {
    file: constants/my_constant_tensor_file.json
    type: tensor<float>(x{},y{})
}
```

This example has a constant tensor with two mapped dimensions, `x` and `y` . An example JSON file with such tensor constant:

```
{
    "cells": [
        { "address": { "x": "a", "y": "b"}, "value": 2.0 },
        { "address": { "x": "c", "y": "d"}, "value": 3.0 }
    ]
}
```

When an application with tensor constants is deployed, the files are distributed to the content nodes before the new configuration is being used by the search nodes. Incremental changes to constant tensors is not supported. When changed, replace the old file with a new one and re-deploy the application or create a new constant with a new name in a new file.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#constant)
