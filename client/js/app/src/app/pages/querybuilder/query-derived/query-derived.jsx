import React from 'react';
import {
  Badge,
  Group,
  Stack,
  Button,
  CopyButton,
  Textarea,
} from '@mantine/core';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';
import { Icon } from 'app/components';

export function QueryDerived() {
  const { input, error } = useQueryBuilderContext('query');

  return (
    <Stack>
      <Group position="apart">
        <Badge variant="filled">Query</Badge>
        <Group spacing="xs">
          <CopyButton value={input}>
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
        </Group>
      </Group>
      <Textarea
        styles={(theme) => ({
          input: {
            fontFamily: theme.fontFamilyMonospace,
            fontSize: theme.fontSizes.xs,
          },
        })}
        value={input}
        error={error}
        onChange={({ target }) => dispatch(ACTION.SET_QUERY, target.value)}
        variant="unstyled"
        minRows={21}
        autosize
      />
    </Stack>
  );
}
