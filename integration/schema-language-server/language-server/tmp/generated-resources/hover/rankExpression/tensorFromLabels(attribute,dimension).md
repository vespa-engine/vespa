Creates a `tensor<double>` with one mapped dimension from the given single value or array attribute. The value(s) must be integers or strings. The attribute is specified as the full feature name, `attribute(name)`. The *dimension* parameter is optional. If omitted the dimension name will be the attribute name.

Example: Given an attribute field `myField` containing the array value:

```
[v1, v2, v3]
```

*tensorFromLabels(attribute(myField), dim)* produces:

```
tensor<double>(dim{}):{ {dim:v1}:1.0, {dim:v2}:1.0, {dim:v3}:1.0} }
```

See *tensorFromWeightedSet* for performance notes.

Default: n/a