## distance-metric

Specifies the distance metric to use with the [nearestNeighbor](https://docs.vespa.ai/en/reference/query-language-reference.html#nearestneighbor) query operator to calculate the distance between document positions and the query position. Only relevant for tensor attribute fields, where each tensor holds one or multiple vectors.

Which distance metric to use depends on the model used to produce the vectors; it must match the distance metric used during representation learning (model learning). If you are using an "off-the-shelf" model to vectorize your data, please ensure that the distance metric matches the distance metric suggested for use with the model. Different type of vectorization models use different type of distance metrics.  
**Important:** When changing the `distance-metric` or `max-links-per-node`, the content nodes must be restarted to rebuild the HNSW index - see [changes that require restart but not re-feed](https://docs.vespa.ai/en/reference/schema-reference.html#changes-that-require-restart-but-not-re-feed)

The calculated distance will be used to select the closest hits for *nearestNeighbor* query operator, but also to build the [HNSW](https://docs.vespa.ai/en/approximate-nn-hnsw.html) index (if specified) and to produce the [distance](https://docs.vespa.ai/en/reference/rank-features.html#distance(dimension,name)) and [closeness](https://docs.vespa.ai/en/reference/rank-features.html#closeness(dimension,name)) ranking features.

<br />

```
distance-metric: [metric]
```

These are the available metrics; the expressions given for *distance* and *closeness* assume a query vector *qv = \[x0, x1, ...\]* and an attribute vector *av = \[y0, y1, ...\]* with same dimension of size *n* for all vectors.

<br />

|        Metric         |                                                                 Description                                                                  |                                                                   distance                                                                    |                                         closeness                                          |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| euclidean             | The normal [euclidean](https://docs.vespa.ai/en/reference/schema-reference.html#euclidean) (aka L2) distance.                                | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> d = ∑ n ( x i - y i ) 2 </math> with range: `[0,inf)`                      | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> 1.0 1.0 + d </math>     |
| angular               | The [angle](https://docs.vespa.ai/en/reference/schema-reference.html#angular) between *qv* and *av* vectors.                                 | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> d = cos-1 ( q → ⋅ a → \| q → \| ⋅ \| a → \| ) </math> with range: `[0,pi]` | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> 1.0 1.0 + d </math>     |
| dotproduct            | Used for [maximal inner product search](https://docs.vespa.ai/en/reference/schema-reference.html#dotproduct).                                | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> d = - ( q → ⋅ a → ) </math> with range: `[-inf,+inf]`                      | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> - d = q → ⋅ a → </math> |
| prenormalized-angular | Assumes normalized vectors, see [note](https://docs.vespa.ai/en/reference/schema-reference.html#prenormalized-angular) below.                | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> d = 1.0 - ( q → ⋅ a → \| q → \| 2 ) </math> with range: `[0,2]`            | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> 1.0 1.0 + d </math>     |
| geodegrees            | Assumes geographical coordinates, see [note](https://docs.vespa.ai/en/reference/schema-reference.html#geodegrees) below.                     | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> d = </math> great-circle in km; range: `[0,20015]`                         | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> 1.0 1.0 + d </math>     |
| hamming               | Only useful for binary tensors using \<int8\> precision, see [note](https://docs.vespa.ai/en/reference/schema-reference.html#hamming) below. | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> d = ∑ n popcount ( x i XOR y i ) </math> ; range: `[0,8*n]`                | <math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> 1.0 1.0 + d </math>     |

### euclidean

The default metric is [euclidean distance](https://en.wikipedia.org/wiki/Euclidean_distance) which is just the length of a line segment between the two points. To compute the euclidean distance directly in a ranking expression instead of fetching one already computed in a nearestNeighbor query operator, use the [euclidean_distance helper function](https://docs.vespa.ai/en/reference/ranking-expressions.html#euclidean-distance-t):

```
    function mydistance() {
        expression: euclidean_distance(attribute(myembedding), query(myqueryvector), mydim))
    }
  
```

<br />

### angular

The *angular* distance metric computes the *angle* between the vectors. Its range is `[0,pi]`, which is the angular distance. This is also known as ordering by [cosine similarity](https://en.wikipedia.org/wiki/Cosine_similarity) where the score function is just the cosine of the angle. To compute the angular distance directly in a ranking expression, use the [cosine_similarity helper function](https://docs.vespa.ai/en/reference/ranking-expressions.html#cosine-similarity-t):

```
    function angle() {
        expression: acos(cosine_similarity(attribute(myembedding), query(myqueryvector), mydim))
    }
  
```

Conversely, the cosine similarity can be recovered from the [distance rank-feature](https://docs.vespa.ai/en/reference/rank-features.html#distance(dimension,name)) when using a nearestNeighbor query operator:

```
    rank-profile cosine {
        first-phase {
            expression: cos(distance(field, myembedding))
        }
    }
  
```

If possible, it's slightly better for performance to normalize both query and document vectors to the same L2 norm and use the `prenormalized-angular` metric instead; but note that returned distance and closeness will be differerent.

### dotproduct

The *dotproduct* distance metric is used to *mathematically transform* a "maximum inner product" search into a form where it can be solved by nearest neighbor search, where the dotproduct is used as a score directly (large positive dotproducts are considered "nearby"). Internally an extra dimension is added (ensuring that all vectors are normalized to the same length) and a distance similar to *prenormalized-angular* is used to build the HNSW index. For details, see [this high level guide](https://towardsdatascience.com/maximum-inner-product-search-using-nearest-neighbor-search-algorithms-c125d24777ef) based on [section 3.1 Order Preserving Transformations in this paper](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/XboxInnerProduct.pdf).

Note that the *distance* and *closeness* rank-features will not have the usual semantic meanings when using the *dotproduct* distance metric. In particular, *closeness* will just return the dot product
<math xmlns="http://www.w3.org/1998/Math/MathML" display="inline"> ∑ n ( x i \* y i ) </math> which may have any negative or positive value, and *distance* is just the negative dot product. If a normalized closeness in range `[0,1]` is needed, an appropriate [sigmoid function](https://en.wikipedia.org/wiki/Sigmoid_function) must be applied. For example, if your attribute is named "foobar", and the maximum dotproduct seen is around 4000, the expression `sigmoid(0.001*closeness(field,foobar))` could be a possible choice.

The *dotproduct* distance metric is useful for some vectorization models, including matrix factorization, that use "maximum inner product" (MIP), with vectors that aren't normalized. These models use both direction and magnitude.

### prenormalized-angular

The *prenormalized-angular* distance metric **must only be used** when **both** query and document vectors are normalized. This metric was previously named "innerproduct" and required unit length vectors; the new version computes the length of the query vector once and assumes all other vectors are of the same length.

Using *prenormalized-angular* with vectors that are not normalized causes unpredictable nearest neighbor search, and is observed to give very bad results both for performance and quality.

The length, magnitude, or norm of a vector *x* is calculated as `length = sqrt(sum(pow(xi,2)))`. The unit length normalized vector is then given by `[xi/length]`. Zero vectors may not be used at all.

The Vespa *prenormalized-angular* computes the [cosine similarity](https://en.wikipedia.org/wiki/Cosine_similarity) and uses `1.0 - cos(angle)` as the distance metric. It gives exactly the same ordering as `angular` distance, but with a distance in the range \[0,2\], since cosine similarity has range \[1,-1\], so the end result is 0.0 for same direction vectors, 1.0 for a right angle, and 2.0 for vectors with exactly opposite directions. Getting the cosine score (or angle) is therefore easy:

```
    rank-profile cosine {
        first-phase {
          expression: 1.0 - distance(field, embedding)
        }
        function angle() {
          expression: acos(1.0 - distance(field, embedding))
        }
    }
  
```

To compute the cosine similarity directly in a ranking expression instead of fetching one already computed in a nearestNeighbor query operator, use the [cosine_similarity helper function](https://docs.vespa.ai/en/reference/ranking-expressions.html#cosine-similarity-t) :

```
    function mysimilarity() {
        expression: cosine_similarity(attribute(myembedding), query(myqueryvector), mydim))
    }
  
```

### geodegrees

The *geodegrees* distance metric is only valid for geographical coordinates (two-dimensional vectors containing latitude and longitude on Earth, in degrees). It computes the great-circle distance (in kilometers) between two geographical points using the [Haversine formula](https://en.wikipedia.org/wiki/Haversine_formula). See [geodegrees system test](https://github.com/vespa-engine/system-test/blob/master/tests/search/nearest_neighbor/geo.sd) for an example.

### hamming

The [Hamming distance](https://en.wikipedia.org/wiki/Hamming_distance) metric counts the number of dimensions where the vectors have different coordinates. This isn't useful for floating-point data since it means you only get 1 bit of information from each floating-point number. Instead, it should be used for binary data where each bit is considered a separate coordinate. Practically, this means you should use the `int8` [cell value type](https://docs.vespa.ai/en/performance/feature-tuning.html#cell-value-types) for your tensor, with the usual encoding from bit pattern to numerical value, for example:

* `00000000` → `0` (hex `00`)
* `00010001` → `17` (hex `11`)
* `00101010` → `42` (hex `2A`)
* `01111111` → `127` (hex `7F`)
* `10000000` → `-128` (hex `80`)
* `10000001` → `-127` (hex `81`)
* `11111110` → `-2` (hex `FE`)
* `11111111` → `-1` (hex `FF`)

Feeding data for this use case may be done with ["hex dump"](https://docs.vespa.ai/en/reference/document-json-format.html#tensor-hex-dump) format instead of numbers in range `[-128,127]` both to have a more natural format for representing binary data, and to avoid the overhead of parsing a large json array of numbers.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#distance-metric)
