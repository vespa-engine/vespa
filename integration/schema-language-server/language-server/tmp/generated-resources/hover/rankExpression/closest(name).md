Used with the [nearestNeighbor](https://docs.vespa.ai/en/reference/query-language-reference.html#nearestneighbor) query operator and a tensor field attribute *name* storing multiple vectors per document. This feature returns a tensor with one mapped dimension and one point with a value of 1.0, where the label of that point indicates which document vector was closest to the query vector in the nearest neighbor search.

Given a tensor field with type `tensor<float>(m{},x[3])` used with the *nearestNeighbor* operator, an example output of this feature is:

```
    tensor<float>(m{}):{ 3: 1.0 }
```

In this example, the document vector with label *3* in the mapped *m* dimension was closest to the query vector.

Default: {}