import React from 'react';
import {
  Badge,
  Button,
  Group,
  Stack,
  CopyButton,
  Textarea,
} from '@mantine/core';
import { DownloadJaeger } from 'app/pages/querybuilder/query-response/download-jaeger';
import { useQueryBuilderContext } from 'app/pages/querybuilder/context/query-builder-provider';
import { Icon } from 'app/components';

export function QueryResponse() {
  const response = useQueryBuilderContext((ctx) => ctx.http.response);

  return (
    <Stack>
      <Group position="apart">
        <Badge variant="filled">Response</Badge>
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
          <DownloadJaeger
            variant="outline"
            size="xs"
            compact
            response={response}
          />
        </Group>
      </Group>
      <Textarea
        styles={(theme) => ({
          input: {
            fontFamily: theme.fontFamilyMonospace,
            fontSize: theme.fontSizes.xs,
          },
        })}
        value={response ?? ''}
        variant="unstyled"
        minRows={21}
        autosize
      />
    </Stack>
  );
}
