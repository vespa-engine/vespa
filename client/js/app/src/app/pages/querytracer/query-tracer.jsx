import React, { useState } from 'react';
import DownloadJSONButton from '../querybuilder/Components/Buttons/DownloadJSONButton';
import { Container } from 'app/components';

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
      <DownloadJSONButton response={response}>
        Download in Jeager format
      </DownloadJSONButton>
    </Container>
  );
}
