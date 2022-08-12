import { Button, Modal, Stack, Textarea } from '@mantine/core';
import React, { useState } from 'react';
import { Icon } from 'app/components';
import {
  ACTION,
  dispatch,
  jsonToInputs,
} from 'app/pages/querybuilder/context/query-builder-provider';

function PasteForm({ close }) {
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');

  function onSubmit() {
    try {
      const json = JSON.parse(query);
      try {
        jsonToInputs(json);
        dispatch(ACTION.SET_QUERY, query);
        close();
      } catch (error) {
        setError(`Invalid Vespa query: ${error.message}`);
      }
    } catch (error) {
      setError(`Invalid JSON: ${error.message}`);
    }
  }

  return (
    <Stack>
      <Textarea
        placeholder="Your Vespa query JSON"
        error={error}
        minRows={34}
        value={query}
        onChange={({ target }) => setQuery(target.value)}
        autosize
      />
      <Button leftIcon={<Icon name="paste" />} onClick={onSubmit}>
        Save
      </Button>
    </Stack>
  );
}

export function PasteModal() {
  const [opened, setOpened] = useState(false);
  const close = () => setOpened(false);

  return (
    <>
      <Modal
        opened={opened}
        onClose={close}
        title="Vespa Query"
        overlayColor="white"
        shadow="1px 3px 10px 2px rgb(0 0 0 / 20%)"
        padding="xl"
        size="xl"
        centered
      >
        <PasteForm close={close} />
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
