import React from 'react';
import {
  Badge,
  Button,
  Group,
  Stack,
  CopyButton,
  Textarea,
} from '@mantine/core';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';
import { Icon } from 'app/components';

export function QueryResponse() {
  const response = useQueryBuilderContext((ctx) => ctx.http.response);

  return (
    <Stack>
      <Group position="apart">
        <Badge variant="filled">Query response</Badge>
        <Group spacing="xs">
          <CopyButton value={response}>
            {({ copied, copy }) => (
              <Button
                leftIcon={<Icon name={copied ? 'check' : 'copy'} />}
                color={copied ? 'teal' : 'blue'}
                variant="outline"
                onClick={copy}
                size="xs"
                compact
              >
                Copy
              </Button>
            )}
          </CopyButton>
          <Button
            leftIcon={<Icon name="download" />}
            variant="outline"
            size="xs"
            compact
          >
            Jaeger Format
          </Button>
        </Group>
      </Group>
      <Textarea
        styles={{
          root: { height: '100%' },
          wrapper: { height: '100%' },
          input: { height: '100%' },
        }}
        value={response ?? ''}
        variant="unstyled"
        minRows={21}
        autosize
      />
    </Stack>
  );
}
