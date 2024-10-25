# Factory Reporter Buildkite Plugin
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

Reports a build to Vespa Factory

## Example

Add the following to your `pipeline.yml`:

```yml
steps:
  - command: ls
    plugins:
      - ./.buildkite/plugins/factory-reporter:
          pipeline-id: 123
          first-step: true
```

## Configuration

### `pipeline-id` (Required, integer)

The id of the pipeline

### `first-step` (Required, boolean)

Set to true if this is the first step in the pipeline


