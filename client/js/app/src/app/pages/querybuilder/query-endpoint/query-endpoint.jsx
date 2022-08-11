import React, { useState } from 'react';
import { Select, TextInput, Button } from '@mantine/core';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';
import { Container, Content } from 'app/components';

// TODO: notify when error
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
    .catch((error) => dispatch(ACTION.SET_HTTP, { error }));
}

export default function QueryEndpoint() {
  const httpMethods = ['POST', 'GET'];
  const [method, setMethod] = useState('POST');
  const [url, setUrl] = useState('http://localhost:8080/search/');
  const query = useQueryBuilderContext((ctx) => ctx.query.input);
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
        <Button radius={0} onClick={() => send(method, url, query)}>
          Send
        </Button>
      </Container>
    </Content>
  );
}
