import React from 'react';
import {
  Badge,
  Group,
  JsonInput,
  Stack,
  Button,
  CopyButton,
} from '@mantine/core';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';
import { Icon } from 'app/components';
import { PasteModal } from 'app/pages/querybuilder/query-derived/paste-modal';

export function QueryDerived() {
  const query = useQueryBuilderContext((ctx) => ctx.query.input);
  return (
    <Stack>
      <Group position="apart">
        <Badge variant="filled">Query</Badge>
        <Group spacing="xs">
          <CopyButton value={query}>
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
          <PasteModal />
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
        variant={'unstyled'}
        minRows={21}
        formatOnBlur
        autosize
      />
    </Stack>
  );
}
