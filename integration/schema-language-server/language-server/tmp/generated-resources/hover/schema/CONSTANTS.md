## constants

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). List of constants available in ranking expressions, resolved and optimized at configuration time.

Constants are inherited from inherited profiles, and from the schema itself.

```
constants {
    name [type]?: value|file:[path]
}
```

| Name  |                                                                                                                                                                                                                  Description                                                                                                                                                                                                                  |
|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name  | The name of the constant, written either the full feature name `constant(myName)`, or just as `name`.                                                                                                                                                                                                                                                                                                                                         |
| type  | The type of the constant, either `double` or a [tensor type](https://docs.vespa.ai/en/reference/tensor.html#tensor-type-spec). If omitted, the type is double.                                                                                                                                                                                                                                                                                |
| value | A number, a [tensor on literal form](https://docs.vespa.ai/en/reference/tensor.html#tensor-literal-form), or `file:` followed by a path from the application package root to a file containing the constant. The file must be stored in a valid [tensor JSON Format](https://docs.vespa.ai/en/reference/constant-tensor-json-format.html) and end with `.json`. The file may be lz4 compressed, in which case the ending must be `.json.lz4`. |

Constant examples:

```
constants {
    myDouble: 0.5
    constant(myOtherDouble) double: 0.6
    constant(myArray) tensor(x[3]):[1, 2, 3]
    constant(myMap) tensor(key{}]):{key1: 1.0, key2: 2.0}
    constant(myLargeTensor) tensor(x[10000]): file:constants/myTensor.json.lz4
}
```

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#constants)
