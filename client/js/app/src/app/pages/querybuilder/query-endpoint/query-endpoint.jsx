import React, { useState } from 'react';
import { Select, TextInput, Button } from '@mantine/core';
import { errorMessage } from 'app/libs/notification';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';
import { Container, Content } from 'app/components';

function send(method, url, query) {
  dispatch(ACTION.SET_HTTP, { loading: true });
  fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json;charset=utf-8' },
    body: query,
  })
    .then((response) => response.json())
    .then((result) =>
      dispatch(ACTION.SET_HTTP, {
        response: JSON.stringify(result, null, 4),
      })
    )
    .catch((error) => {
      errorMessage(error.message);
      dispatch(ACTION.SET_HTTP, {});
    });
}

export default function QueryEndpoint() {
  const httpMethods = ['POST', 'GET'];
  const [method, setMethod] = useState('POST');
  const [url, setUrl] = useState('http://localhost:8080/search/');
  const query = useQueryBuilderContext((ctx) => ctx.query.input);
  const loading = useQueryBuilderContext((ctx) => ctx.http.loading);

  return (
    <Content>
      <Container sx={{ gridTemplateColumns: 'max-content auto max-content' }}>
        <Select
          data={httpMethods}
          onChange={setMethod}
          value={method}
          radius={0}
        />
        <TextInput
          onChange={(event) => setUrl(event.currentTarget.value)}
          value={url}
          radius={0}
        />
        <Button
          radius={0}
          onClick={() => send(method, url, query)}
          loading={loading}
        >
          Send
        </Button>
      </Container>
    </Content>
  );
}
