Calculates the sparse dot product between query term weights and match weights for the given field. Example: A weighted set string field X:

```
"X": {
    "x": 10,
    "y": 20,
    "z": 30
}
```

For the query (x!2 OR y!4), the nativeDotProduct(X) feature will have the value 100 (10\*2+20\*4) for that document.  
**Note:** `nativeDotProduct` and `nativeDotProduct(field)` is less optimal for computing the dot product - consider using [dotProduct(name,vector)](https://docs.vespa.ai/en/reference/rank-features.html#dotProduct(name,vector)).

Default: 0