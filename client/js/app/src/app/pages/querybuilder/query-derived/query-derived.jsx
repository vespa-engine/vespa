import React from 'react';
import {
  Badge,
  Group,
  Stack,
  Button,
  CopyButton,
  Textarea,
} from '@mantine/core';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';
import { Icon } from 'app/components';
import { PasteModal } from 'app/pages/querybuilder/query-derived/paste-modal';

export function QueryDerived() {
  const query = useQueryBuilderContext('query');

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
      <Textarea
        styles={(theme) => ({
          input: {
            fontFamily: theme.fontFamilyMonospace,
            fontSize: theme.fontSizes.xs,
          },
        })}
        value={query}
        variant="unstyled"
        minRows={21}
        autosize
      />
    </Stack>
  );
}
