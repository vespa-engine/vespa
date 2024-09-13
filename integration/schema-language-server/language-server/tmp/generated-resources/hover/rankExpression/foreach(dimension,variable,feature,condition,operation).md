*foreach* iterates over a set of feature output values and performs an operation on them. Only the values where the condition evaluates to true are considered for the operation. The result of this operation is returned.

* *dimension* : Specifies what to iterate over. This can be:
  * *terms* : All query term indices, from 0 and up to [maxTerms](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#foreach).
  * *fields*: All index field names.
  * *attributes*: All attribute field names.
* *variable*: The name of the variable 'storing' each of the items you are iterating over.
* *feature* : The name of the feature you want to use the output value from. Use the *variable* as part of the feature name, and for each item you iterate over this *variable* is replaced with the actual item. Note that the variable replacement is a simple string replace, so you should use a variable name that is not in conflict with the feature name.
* *condition* : The condition used on each feature output value to find out if the value should be considered by the operation. The condition can be:
  * *\>a*: Use feature output if greater than number a.
  * *\<a*: Use feature output if less than number a.
  * *true*: Use all feature output values.
* *operation* : The operation you want to perform on the feature output values. This can be:
  * *sum*: Calculate the sum of the values.
  * *product*: Calculate the product of the values.
  * *average*: Calculate the average of the values.
  * *max*: Find the max of the values.
  * *min*: Find the min of the values.
  * *count*: Count the number of values.

Lets say you want to calculate the average score of the *fieldMatch* feature for all index fields, but only consider the scores larger than 0. Then you can use the following setup of the *foreach* feature:

`foreach(fields,N,fieldMatch(N), ">0", average)`.

Note that when using the conditions *\>a* and *\<a* the arguments must be quoted.

You can also specify a ranking expression in the *foreach* feature by using the *rankingExpression* feature. The *rankingExpression* feature takes the expression as the first and only parameter and outputs the result of evaluating this expression. Let's say you want to calculate the average score of the squared *fieldMatch* feature score for all index fields. Then you can use the following setup of the *foreach* feature:

`foreach(fields, N, rankingExpression("fieldMatch(N)*fieldMatch(N)"), true, average)`

Note that you must quote the expression passed in to the *rankingExpression* feature.

Default: n/a