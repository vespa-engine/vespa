Returns a normalized measure of the number of occurrence of the terms of the query:

$$\\frac{\\sum_{\\text{all query terms}}(min(\\text{number of occurrences of the term},maxOccurrences))}{(\\text{query term count} × 100)}$$

This is 1 if there are many occurrences of the query terms, and 0 if there are none.

This number is not relative to the field length, so it is suitable for uses of occurrence to denote relative importance between matched terms (i.e. fields containing keywords, not normal text).

Default: 0