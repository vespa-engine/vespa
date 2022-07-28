import React, { useContext } from 'react';
import DownloadJSONButton from '../querybuilder/Components/Buttons/DownloadJSONButton';
import { ResponseContext } from '../querybuilder/Components/Contexts/ResponseContext';
import { Container } from 'app/components';

export function QueryTracer() {
  const { response, setResponse } = useContext(ResponseContext);

  const updateResponse = (e) => {
    setResponse(e.target.value);
  };

  return (
    <Container>
      <textarea
        cols="70"
        rows="25"
        value={response}
        onChange={updateResponse}
      ></textarea>
      <DownloadJSONButton>
        Convert to Jeager format and download trace
      </DownloadJSONButton>
    </Container>
  );
}
