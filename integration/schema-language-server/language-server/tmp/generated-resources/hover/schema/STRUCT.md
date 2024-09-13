## struct

Contained in [document](https://docs.vespa.ai/en/reference/schema-reference.html#document). Defines a composite type. A struct consists of zero or more fields that the user can access together as one. The struct has to be defined before it is used as a type in a field specification.

```
struct [name] {
    [body]
}
```

The body of a struct is optional and may contain:

|                                  Name                                   |  Occurrence  |       Description       |
|-------------------------------------------------------------------------|--------------|-------------------------|
| [field](https://docs.vespa.ai/en/reference/schema-reference.html#field) | Zero to many | A field of this struct. |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#struct)
