Convenience function for generating token sequence input to Transformer models. Creates a tensor with dimensions `d0[1], d1[length]`, where `d0` is the batch dimension and `d1` is the maximum length of the token sequence. Assumes the inputs are zero-padded tensors representing token sequences. The result is the token sequence:

`start_sequence_id + input_1 + sep_sequence_id + input_2 + sep_sequence_id + ... + 0's`

* *start_sequence_id* The start sequence id, typically *1*
* *sep_sequence_id* The separator sequence id, typically *2*
* *length*: The maximum length of the token sequence
* *input_N*: Where to retrieve input from. At least one is required.

The inputs are typically retrieved from the query, document attributes or constants. For instance, `customTokenInputIds(1,2,128, query(my_input), attribute(my_field))` where input types are:

* `query(my_input): tensor(d0[32])`
* `attribute(my_field): tensor(d0[128])`

Default: n/a