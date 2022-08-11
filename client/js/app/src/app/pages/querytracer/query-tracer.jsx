import React, { useState } from 'react';
import { Container, DownloadJson } from 'app/components';

export function QueryTracer() {
  const [response, setResponse] = useState('');

  return (
    <Container>
      <textarea
        cols="70"
        rows="25"
        value={response}
        onChange={({ target }) => setResponse(target.value)}
      ></textarea>
      <DownloadJson response={response}>Download in Jeager format</DownloadJson>
    </Container>
  );
}
