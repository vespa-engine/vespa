import React from 'react';
import { Select, TextInput, ActionIcon, Button } from '@mantine/core';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';
import { Container, Icon } from 'app/components';

export default function QueryInput() {
  const { children, type } = useQueryBuilderContext('query');
  return <Inputs type={type.children} inputs={children} />;
}

function Inputs({ id, type, inputs }) {
  const usedTypes = inputs.map(({ type }) => type.name);
  const remainingTypes = Object.fromEntries(
    Object.entries(type).filter(([name]) => !usedTypes.includes(name))
  );
  const firstRemaining = Object.keys(remainingTypes)[0];
  return (
    <Container sx={{ backgroundColor: 'gold', rowGap: '5px' }}>
      {inputs.map(({ id, input, type, children }) => (
        <Input
          key={id}
          types={remainingTypes}
          {...{ id, input, type, children }}
        />
      ))}
      {firstRemaining && (
        <Button
          leftIcon={<Icon name="plus" />}
          onClick={() =>
            dispatch(ACTION.INPUT_ADD, { id, type: firstRemaining })
          }
        >
          Add property
        </Button>
      )}
    </Container>
  );
}

function Input({ id, input, types, type, children }) {
  const options = { [type.name]: type, ...types };
  return (
    <Container
      sx={{
        display: 'flex',
        // gridTemplateColumns: children
        //   ? 'minmax(0, 1fr) max-content'
        //   : 'minmax(0, 1fr) minmax(0, 1fr) max-content',
        alignItems: 'center',
        gap: '5px',
        backgroundColor: 'aqua',
      }}
    >
      <Container sx={{ display: 'flex' }}>
        <Select
          sx={{ flex: 1.4 }}
          data={Object.values(options).map(({ name }) => name)}
          onChange={(value) =>
            dispatch(ACTION.INPUT_UPDATE, {
              id,
              type: types[value],
            })
          }
          value={type.name}
          searchable
        />
        {!children && (
          <TextInput
            sx={{ flex: 1.4 }}
            onChange={(event) =>
              dispatch(ACTION.INPUT_UPDATE, {
                id,
                input: event.currentTarget.value,
              })
            }
            placeholder={type.type}
            value={input}
          />
        )}
        <ActionIcon onClick={() => dispatch(ACTION.INPUT_REMOVE, id)}>
          <Icon name="circle-minus" />
        </ActionIcon>
      </Container>
      {children && (
        <Container sx={{ backgroundColor: 'green' }}>
          <Inputs id={id} type={type.children} inputs={children} />
        </Container>
      )}
    </Container>
  );
}
