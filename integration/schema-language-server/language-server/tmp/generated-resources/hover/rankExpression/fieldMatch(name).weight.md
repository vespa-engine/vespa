The normalized weight of this match relative to the whole query: The sum of the weights of all *matched* terms/the sum of the weights of all *query* terms. If all the query terms were matched, this is 1. If no terms were matched, or these matches has weight zero this is 0.

As the sum of this number over all the terms of the query is always 1, sums over all fields of normalized rank features for each field multiplied by this number for the same field will produce a normalized number.

Note that this scales with the number of matched query terms in the field. If you want a component which does not, divide by matches.

Default: 0