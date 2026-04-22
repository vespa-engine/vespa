<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# Proposal: Structured Generative Retrieval and Semantic IDs

Status: Draft

## Motivation

Vespa already has strong support for encoder-style model integration:

- `Embedder`: one text input to one tensor or token sequence
- `FieldGenerator`: indexing-time prompt to field value generation
- `LanguageModel`: prompt to text completion

This fits SPLADE and dense embedding well, but it does not fit a Semantic ID or GenSearch style flow where:

1. Indexing quantizes an embedding into a discrete multi-level code.
2. Query serving builds a structured prompt from user taste, recent actions, context, and query text.
3. A generative model returns multiple scored SID candidates through beam search.
4. Generation may be constrained by a prefix trie so every beam remains on a valid SID path.
5. Generated SIDs are resolved to candidate documents, optionally with prefix backoff.

The current model APIs do not represent that flow directly.

## Why Current APIs Are Not Enough

### `Embedder` is the wrong shape

`Embedder` assumes `text -> tensor` or `text -> token ids`.

That works for SPLADE because SPLADE is still one input string to one sparse tensor. A SID generator is different:

- input is not just one text string
- output is not one tensor
- serving needs multiple scored sequences, not just one embedding
- constrained decoding is part of generation

Trying to force this into `Embedder` would overload it with behavior that is not embedding.

### `FieldGenerator` is indexing-only

`FieldGenerator` is useful for document processing, but it is tied to "generate a field value for a destination field". It is not a reusable query-time API for returning beams, token ids, or decoding constraints.

### `LanguageModel` is too text-centric

`LanguageModel` currently returns text completions only. It has no first-class support for:

- structured prompt sections
- returning token ids alongside text
- beam search results
- constrained decoding
- SID-specific post-processing

## Requirements From Semantic ID / GenSearch Workloads

For the motivating workload described in `Semantic IDs and GenSearch.md`, Vespa needs to support:

- `embedding -> SID` quantization at indexing time
- structured prompts such as `user taste </s> recent actions </s> context </s> query`
- multiple scored output sequences at serving time
- optional constrained beam search using a prefix trie
- application-level or future built-in `SID -> candidates` lookup

The important design point is that these are additive capabilities. They should not weaken or complicate the existing encoder-style APIs.

## Proposed Additions

### 1. `StructuredPrompt`

Add a prompt type that keeps ordered named sections while still being serializable to a flat string.

This lets applications build prompts like:

```java
StructuredPrompt prompt = StructuredPrompt.builder()
        .add("user-taste", userTaste)
        .add("recent-actions", recentActions)
        .add("context", context)
        .add("query", query)
        .separator(" </s> ")
        .build();
```

This is useful even outside generative retrieval and can coexist with `StringPrompt`.

### 2. `Quantizer`

Add an indexing-time component contract for `Tensor -> DiscreteSequence`.

This is the missing counterpart to `Embedder`. It separates:

- encoder models that create embeddings
- quantizers that convert embeddings into semantic IDs or other discrete paths

Near term, this can be used from custom indexing components or processors. A future indexing expression could expose it more directly.

### 3. `SequenceGenerator`

Add a query-time contract for discrete generation:

- prompt in
- beams out
- token ids included
- optional decoding constraint

This is the core missing runtime surface for SID generation.

The proposed API is intentionally lower-level than retrieval. It stops at sequence generation and does not try to own result set semantics yet.

### 4. Keep candidate lookup above the generator, initially

The `SID -> document ids` lookup and prefix backoff logic should initially live one layer above `SequenceGenerator`, likely in a searcher or application component.

That keeps the first API addition narrow:

- `Quantizer` owns indexing-time discrete encoding
- `SequenceGenerator` owns query-time sequence generation
- lookup and retrieval policy remain composable

If this becomes a broadly used serving pattern, Vespa can later add a higher-level `SequenceResolver` or `GenerativeRetriever`.

## Example Flows

### Indexing flow

```java
Tensor embedding = embedder.embed(text, embedContext, tensorType);
DiscreteSequence sid = quantizer.quantize(embedding, new Quantizer.Context("listing.sid"));
```

Document fields can then store:

- full SID tokens
- prefix fields
- optional rendered SID text

### Query flow

```java
StructuredPrompt prompt = StructuredPrompt.builder()
        .add("user-taste", userTaste)
        .add("recent-actions", recentActions)
        .add("context", context)
        .add("query", query)
        .separator(" </s> ")
        .build();

List<SequenceGenerator.GeneratedSequence> beams =
        generator.generate(prompt,
                           new SequenceGenerator.Context("query(sids)"),
                           new SequenceGenerator.Options(8, 128, 128, trieConstraint));
```

The application or searcher then:

1. resolves generated SID sequences
2. applies exact match or prefix backoff policy
3. turns those candidates into retrieval or ranking inputs

## Proposed Configuration Surface

The first implementation does not need new XML syntax. It can use the existing generic component mechanism:

```xml
<container version="1.0">
    <component id="sid-quantizer"
               class="ai.vespa.semanticid.ResidualQuantizer"
               bundle="semanticid">
        <config name="ai.vespa.semanticid.quantizer">
            <codebook path="models/codebook.json"/>
        </config>
    </component>

    <component id="sid-generator"
               class="ai.vespa.semanticid.OnnxSequenceGenerator"
               bundle="semanticid">
        <config name="ai.vespa.semanticid.generator">
            <model path="models/qwen-sid.onnx"/>
            <tokenizer path="models/tokenizer.json"/>
            <prefix-trie path="models/sid-prefix-trie.bin"/>
        </config>
    </component>
</container>
```

If this proves broadly useful, Vespa can later add typed components similar to `splade-embedder`, for example:

```xml
<container version="1.0">
    <component id="sid-quantizer" type="semantic-id-quantizer">
        <codebook-model path="models/codebook.json"/>
    </component>

    <component id="sid-generator" type="sequence-generator">
        <transformer-model path="models/qwen-sid.onnx"/>
        <tokenizer-model path="models/tokenizer.json"/>
        <prefix-trie path="models/sid-prefix-trie.bin"/>
    </component>
</container>
```

## Scope Of This PR

This draft PR adds only proposal-level API sketches:

- `StructuredPrompt`
- `DiscreteSequence`
- `Quantizer`
- `SequenceGenerator`

It does not:

- add indexing language syntax
- add query language syntax
- add config-model typed components
- add trie-aware decoding implementations
- add searcher integration

That is deliberate. The goal is to let the Vespa team review the API shape before plumbing it through runtime and config layers.

## Design Principles

- Keep encoder-style APIs simple and unchanged.
- Add discrete generation as a parallel capability, not a special case of embedding.
- Make structured prompts first-class, but still serializable to plain strings.
- Separate generation from candidate resolution.
- Allow constrained decoding without forcing it.

## Open Questions

1. Should `SequenceGenerator` live beside `LanguageModel`, or stay aligned with `Embedder` and `FieldGenerator` under `com.yahoo.language.process`?
2. Should a future query syntax expose this directly, or should it remain a searcher-level capability?
3. Should constrained decoding be modeled as a generic token constraint, or should there be a stronger trie-specific abstraction?
4. When productized, should candidate lookup/backoff remain outside the generator, or become a first-class `GenerativeRetriever` component?
5. Should a future indexing expression expose quantization directly, for example `quantize(quantizer-id)`?

## Relation To SPLADE

SPLADE should remain in the current embedder framework. It is still an encoder-style model: one string in, one sparse tensor out.

Semantic ID generation is different enough that it deserves a separate API:

- indexing side: `embedding -> discrete sequence`
- query side: `structured prompt -> scored discrete sequences`

That keeps both abstractions honest.
