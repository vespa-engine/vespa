import React from 'react';
import { Badge, Group, JsonInput, Stack, Button } from '@mantine/core';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';
import { Icon } from 'app/components';

export function QueryDerived() {
  const query = useQueryBuilderContext((ctx) => ctx.query.input);
  return (
    <Stack>
      <Group position="apart">
        <Badge variant="filled">Query</Badge>
        <Group spacing="xs">
          <Button
            leftIcon={<Icon name="paste" />}
            variant="outline"
            size="xs"
            compact
          >
            Paste JSON
          </Button>
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
        value={query}
        validationError="Invalid json"
        minRows={21}
        formatOnBlur
      />
    </Stack>
  );
}
