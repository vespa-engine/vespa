import React, { useState } from 'react';
import { Stack, Textarea } from '@mantine/core';
import { DownloadJeager } from 'app/pages/querybuilder/query-response/download-jeager';

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
      <DownloadJeager fullWidth response={response} />
    </Stack>
  );
}
