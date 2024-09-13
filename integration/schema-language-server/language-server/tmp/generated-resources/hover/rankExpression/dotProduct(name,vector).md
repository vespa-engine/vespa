<br />

**Note:** Most dot product use cases are better solved using [tensors](https://docs.vespa.ai/en/tensor-user-guide.html).

The sparse dot product of the vector represented by the given weighted set attribute and the vector sent down with the query.

You can also do an ordinary full dotproduct by using arrays instead of weighted sets. This will be a lot faster when you have full vectors in the document with more than 5-10% non-zero values. You are also then not limited to integer weights. All the numeric datatypes can be used with arrays, so you have full floating point support. The 32 bit floating point type yields the fastest execution.

* *name*: The name of the weighted set string/integer or array of numeric attribute.
* *vector*: The name of the vector sent down with the query.

Each unique string/integer in the weighted set corresponds to a dimension and the belonging weight is the vector component for that dimension. The query vector is set in the [rankproperty.dotProduct.*vector*](https://docs.vespa.ai/en/reference/query-api-reference.html#ranking.properties) query parameter, using syntax `{d1:c1,d2:c2,...}` where *d1* and *d2* are dimensions matching the strings/integers in the weighted set and *c1* and *c2* are the vector components (floating point numbers). The number of dimensions in the weighted set and the query vector do not need to be the same. When calculating the dot product we only use the dimensions present in both the weighted set and the query vector.

When using an array the dimensions is a positive integer starting at 0. If the query is sparse all non given dimensions are zero. That also goes for dimensions that outside of the array size in each document.

Assume a weighted set string attribute X with:

```
"X": {
    "x": 10,
    "y": 20
}
```

for a particular document. The result of using the feature dotProduct(X,Y) with the query vector rankproperty.dotProduct.Y={x:2,y:4} will then be 100 (10\*2+20\*4) for this document.

Arrays can be passed down as `[w1 w2 w3 ...]` or on sparse form `{d1:c1,d2:c2,...}` as is already supported for weighted sets.  
**Note:** When the query vector ends up being the same as the query, it is better to annotate the query terms with weights (see [term weight](https://docs.vespa.ai/en/reference/simple-query-language-reference.html#term-weight)) and use the nativeDotProduct feature instead. This will run more efficiently and improve the correlation between results produced by the WAND operator and the final relevance score.  
**Note:** When using the dotProduct feature, [fast-search](https://docs.vespa.ai/en/attributes.html#fast-search) is not needed, unless also used for searching. When using the dotProduct query operator, use fast-search.

Default: 0