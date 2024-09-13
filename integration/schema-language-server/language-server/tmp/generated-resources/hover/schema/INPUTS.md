## inputs

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). List of inputs: Query features consumed by ranking expressions in this profile.

Query features are set either as a [request property](https://docs.vespa.ai/en/reference/query-api-reference.html#ranking.features), or equivalently from a [Searcher](https://docs.vespa.ai/en/searcher-development.html), by calling `query.getRanking().getFeatures().put("query(myInput)", myValue)`.

Query feature types can also be declared in [query profile types](https://docs.vespa.ai/en/query-profiles.html#query-profile-types), but declaring inputs in the profile needing them is usually preferable.

Inputs are inherited from inherited profiles.

```
inputs {
    name [type]? (: value)?
}
```

| Name  |                                                                                     Description                                                                                      |
|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name  | The name of the inputs, written either the full feature name `query(myName)`, or just as `name`.                                                                                     |
| type  | The type of the constant, either `double` or a [tensor type](https://docs.vespa.ai/en/reference/tensor.html#tensor-type-spec). If omitted, the type is double.                       |
| value | An optional default module, used if this input is not set in the query. A number, or a [tensor on literal form](https://docs.vespa.ai/en/reference/tensor.html#tensor-literal-form). |

Input examples:

```
inputs {
    myDouble: 0.5
    query(myOtherDouble) double
    query(myArray) tensor(x[3])
    query(myMap) tensor(key{}]):{key1: 1.0, key2: 2.0}
}
```

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#inputs)
