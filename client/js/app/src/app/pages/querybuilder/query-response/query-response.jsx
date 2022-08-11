import React from 'react';
import {
  Badge,
  Button,
  Group,
  JsonInput,
  Stack,
  CopyButton,
} from '@mantine/core';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';
import { Icon } from 'app/components';

export function QueryResponse() {
  const response = useQueryBuilderContext((ctx) => ctx.http.response);
  return (
    <Stack>
      <Group position="apart">
        <Badge variant="filled">Query reponse</Badge>
        <Group spacing="xs">
          <CopyButton value={response}>
            {({ copied, copy }) => (
              <Button
                leftIcon={<Icon name="copy" />}
                color={copied ? 'teal' : 'blue'}
                variant="outline"
                onClick={copy}
                size="xs"
                compact
              >
                {copied ? 'Copied' : 'Copy'}
              </Button>
            )}
          </CopyButton>
          <Button
            leftIcon={<Icon name="download" />}
            variant="outline"
            size="xs"
            compact
          >
            Jeager Format
          </Button>
        </Group>
      </Group>
      <JsonInput
        styles={{
          root: { height: '100%' },
          wrapper: { height: '100%' },
          input: { height: '100%' },
        }}
        value={response}
        minRows={21}
      />
    </Stack>
  );
}
