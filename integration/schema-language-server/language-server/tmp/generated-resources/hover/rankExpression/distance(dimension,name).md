Used with the [nearestNeighbor](https://docs.vespa.ai/en/reference/query-language-reference.html#nearestneighbor) query operator. A number which is close to 0 when a point vector in the document is close to a matching point vector in the query. The document vectors and the query vector must be the same tensor type, with one indexed dimension of size N, representing a point in an N-dimensional space.

* *dimension* : Specifies the dimension of *name* . This must be either the string `field` or the string `label`.

  When using `field`, the name given must be a field with a tensor attribute of appropriate type. Often used when the document type has only one vector field, see [example](https://docs.vespa.ai/en/nearest-neighbor-search.html#minimal-example).

  When using `label`, queries are assumed to contain a [nearestNeighbor](https://docs.vespa.ai/en/reference/query-language-reference.html#nearestneighbor) query item with a [label](https://docs.vespa.ai/en/reference/query-language-reference.html#label) that matches the given *name* . This is useful when having multiple vector fields, where `distance()` then maps to the nearestNeighbor operator with the field configured. [Example](https://docs.vespa.ai/en/nearest-neighbor-search-guide.html#using-label).
* *name*: The value of the field name or label.

The output value depends on the [distance metric](https://docs.vespa.ai/en/reference/schema-reference.html#distance-metric) used. The default is the Euclidean distance between the "n"-dimensional query point "d" and the point "d" in the document tensor field: $$ distance = \\sqrt{\\sum_{i=1}\^n (q_i - d_i)\^2} $$

When the tensor field stores multiple vectors per document, the minimum distance between the vectors of a document and the query vector is used in the calculation above.

Default: max double value