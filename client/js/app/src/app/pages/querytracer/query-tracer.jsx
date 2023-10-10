// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import React, { useState } from 'react';
import { Stack, Textarea } from '@mantine/core';
import { DownloadJaeger } from 'app/pages/querybuilder/query-response/download-jaeger';

export function QueryTracer() {
  const [response, setResponse] = useState('');

  return (
    <Stack>
      <Textarea
        styles={(theme) => ({
          input: {
            fontFamily: theme.fontFamilyMonospace,
            fontSize: theme.fontSizes.xs,
          },
        })}
        minRows={21}
        autosize
        value={response}
        onChange={({ target }) => setResponse(target.value)}
      />
      <DownloadJaeger fullWidth response={response} />
    </Stack>
  );
}
