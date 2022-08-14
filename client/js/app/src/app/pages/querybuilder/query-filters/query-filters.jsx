import React from 'react';
import {
  Select,
  TextInput,
  ActionIcon,
  Button,
  Box,
  Stack,
  Badge,
  Group,
} from '@mantine/core';
import { Container, Content, Icon } from 'app/components';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';
import { SHADE } from 'app/styles/theme/colors';

function AddProperty(props) {
  return (
    <Button leftIcon={<Icon name="plus" />} {...props}>
      Add property
    </Button>
  );
}

function Input({ id, value, types, type }) {
  const options = { [type.name]: type, ...types };
  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
        <Select
          sx={{ flex: 1 }}
          data={Object.values(options).map(({ name }) => name)}
          onChange={(type) => dispatch(ACTION.INPUT_UPDATE, { id, type })}
          value={type.name}
          searchable
        />
        {!type.children && (
          <TextInput
            sx={{ flex: 1 }}
            onChange={(event) =>
              dispatch(ACTION.INPUT_UPDATE, {
                id,
                value: event.currentTarget.value,
              })
            }
            placeholder={type.type}
            value={value}
          />
        )}
        <ActionIcon onClick={() => dispatch(ACTION.INPUT_REMOVE, id)}>
          <Icon name="circle-minus" />
        </ActionIcon>
      </Box>
      {type.children && (
        <Box
          py={8}
          sx={(theme) => ({
            borderLeft: `1px dashed ${theme.fn.themeColor(
              'gray',
              SHADE.UI_ELEMENT_BORDER_AND_FOCUS
            )}`,
            marginLeft: '13px',
            paddingLeft: '13px',
          })}
        >
          <Inputs id={id} type={type.children} inputs={value} />
        </Box>
      )}
    </>
  );
}

function Inputs({ id, type, inputs }) {
  const usedTypes = inputs.map(({ type }) => type.name);
  const remainingTypes = Object.fromEntries(
    Object.entries(type).filter(([name]) => !usedTypes.includes(name))
  );
  const firstRemaining = Object.keys(remainingTypes)[0];
  return (
    <Container sx={{ rowGap: '5px' }}>
      {inputs.map(({ id, value, type }) => (
        <Input key={id} types={remainingTypes} {...{ id, value, type }} />
      ))}
      {firstRemaining && (
        <>
          {id != null ? (
            <Container sx={{ justifyContent: 'start' }}>
              <AddProperty
                onClick={() =>
                  dispatch(ACTION.INPUT_ADD, { id, type: firstRemaining })
                }
                variant="subtle"
                size="xs"
                compact
              />
            </Container>
          ) : (
            <AddProperty
              onClick={() =>
                dispatch(ACTION.INPUT_ADD, { id, type: firstRemaining })
              }
              mt={13}
            />
          )}
        </>
      )}
    </Container>
  );
}

export function QueryFilters() {
  const { value, type } = useQueryBuilderContext('params');
  return (
    <Stack>
      <Group>
        <Badge variant="filled">Parameters</Badge>
      </Group>
      <Container sx={{ alignContent: 'start' }}>
        <Content padding={0}>
          <Inputs type={type.children} inputs={value} />
        </Content>
      </Container>
    </Stack>
  );
}
