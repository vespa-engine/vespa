import React, { useState } from 'react';
import { Modal, Button, JsonInput, Stack } from '@mantine/core';
import { Icon } from 'app/components';

export function PasteModal() {
  const [opened, setOpened] = useState(false);
  return (
    <>
      <Modal
        opened={opened}
        onClose={() => setOpened(false)}
        title="Vespa Query"
        overlayColor="white"
        shadow="1px 3px 10px 2px rgb(0 0 0 / 20%)"
        padding="xl"
        size="xl"
        centered
      >
        <Stack>
          <JsonInput
            placeholder="Your Vespa query JSON"
            validationError="Invalid JSON or Vespa invalid query"
            minRows={34}
            formatOnBlur
            autosize
          />
          <Button
            leftIcon={<Icon name="paste" />}
            onClick={() => {
              console.log('paste and close');
            }}
          >
            Paste
          </Button>
        </Stack>
      </Modal>
      <Button
        onClick={() => setOpened(true)}
        leftIcon={<Icon name="paste" />}
        variant="outline"
        size="xs"
        compact
      >
        Paste JSON
      </Button>
    </>
  );
}
