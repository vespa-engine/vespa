import React, { useState } from 'react';
import { Textarea } from '@mantine/core';
import { Container } from 'app/components';
import { DownloadJeager } from 'app/pages/querybuilder/query-response/download-jeager';

export function QueryTracer() {
  const [response, setResponse] = useState('');

  return (
    <Container>
      <Textarea
        styles={{
          root: { height: '100%' },
          wrapper: { height: '100%' },
          input: { height: '100%' },
        }}
        minRows={21}
        autosize
        value={response}
        onChange={({ target }) => setResponse(target.value)}
      ></Textarea>
      <DownloadJeager fullWidth response={response} />
    </Container>
  );
}
