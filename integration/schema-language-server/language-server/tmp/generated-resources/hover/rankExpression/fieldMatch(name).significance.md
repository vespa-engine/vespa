Returns the normalized term significance of the terms of this match relative to the whole query: The sum of the significance of all *matched* terms/the sum of the significance of all *query* terms. If all the query terms were matched, this is 1. If no terms were matched, or if the significance of all the matched terms is zero, this number is zero.

This metric has the same properties as weight.

See the [term(n).significance](https://docs.vespa.ai/en/reference/rank-features.html#term(n).significance) feature for how the significance for a single term is calculated.

Default: 0