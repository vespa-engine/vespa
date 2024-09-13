## function (inline)? \[name\]

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). Define a named function that can be referenced as a part of the ranking expression, or (if having no arguments) as a feature. A function accepts any number of arguments.

```
function [name]([arg1], [arg2], [arg3]) {
    expression: …
}
```

or

```
function [name] ([arg1], [arg2], [arg3]) {
    expression {
        [ranking expression]
        [ranking expression]
        …
}
```

Note that the parenthesis is required after the name. A rank-profile example is shown below:

```
rank-profile default inherits default {
    function myfeature() {
        expression: fieldMatch(title) + freshness(timestamp)
    }
    function otherfeature(foo) {
        expression{ nativeRank(foo, body) }
    }

    first-phase {
        expression: myfeature * 10
    }
    second-phase {
        expression: otherfeature(title) * myfeature
    }
    summary-features: myfeature
}
```

You can not include functions that accept arguments in summary features.

Adding the `inline` modifier will inline this function in the calling expression if it also has no arguments. This is faster for small and cheap functions (and more expensive for others).
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#function-rank)
