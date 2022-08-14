import React from 'react';
import { Select, TextInput, Button } from '@mantine/core';
import { errorMessage } from 'app/libs/notification';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';
import { Container, Content } from 'app/components';

function send(event, method, url, body) {
  event.preventDefault();
  dispatch(ACTION.SET_HTTP, { loading: true });
  fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json;charset=utf-8' },
    body,
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
  const { method, url, fullUrl, body } = useQueryBuilderContext('request');
  const hasError = useQueryBuilderContext((ctx) => ctx.query.error != null);
  const loading = useQueryBuilderContext((ctx) => ctx.http.loading);

  return (
    <Content>
      <form onSubmit={(event) => send(event, method, fullUrl, body)}>
        <Container sx={{ gridTemplateColumns: 'max-content auto max-content' }}>
          <Select
            data={['POST', 'GET']}
            onChange={(value) => dispatch(ACTION.SET_METHOD, value)}
            value={method}
            radius={0}
          />
          <TextInput
            onChange={(e) => dispatch(ACTION.SET_URL, e.currentTarget.value)}
            value={url}
            radius={0}
          />
          <Button
            radius={0}
            type="submit"
            loading={loading}
            disabled={hasError}
          >
            Send
          </Button>
        </Container>
      </form>
    </Content>
  );
}
