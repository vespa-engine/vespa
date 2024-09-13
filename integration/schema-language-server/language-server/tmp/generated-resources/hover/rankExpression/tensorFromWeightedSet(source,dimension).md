Creates a `tensor<double>` with one mapped dimension from the given integer or string weighted set attribute. The attribute is specified as the full feature name, `attribute(name)`. The *dimension* parameter is optional. If omitted the dimension name will be the attribute name.

Example: Given the weighted set:

```
{key1:0, key2:1, key3:2.5}
```

*tensorFromWeightedSet(attribute(myField), dim)* produces:

```
tensor<double>(dim{}):{ {dim:key1}:0.0, {dim:key2}:1.0, {dim:key3}:2.5} }
```

**Note:** This creates a temporary tensor, and has build cost and extra memory is touched. Tensor evaluation is most effective when the cell types of all tensors are equal - use [cell_cast](https://docs.vespa.ai/en/reference/ranking-expressions.html#cell_cast) to enable optimizations. Also, duplicating the field in the schema to a native tensor instead of creating from a set can increase performance.

Default: n/a