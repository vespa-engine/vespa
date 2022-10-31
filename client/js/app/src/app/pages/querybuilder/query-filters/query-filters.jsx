import React from 'react';
import {
  Select,
  TextInput,
  ActionIcon,
  Button,
  Box,
  Stack,
  Switch,
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

function Property({ id, type, types }) {
  if (types)
    return (
      <Select
        sx={{ flex: 1 }}
        data={Object.values({ [type.name]: type, ...types }).map(
          ({ name }) => name
        )}
        onChange={(type) => dispatch(ACTION.INPUT_UPDATE, { id, type })}
        value={type.name}
        searchable
      />
    );

  return (
    <TextInput
      sx={{ flex: 1 }}
      onChange={(event) =>
        dispatch(ACTION.INPUT_UPDATE, {
          id,
          type: event.currentTarget.value,
        })
      }
      placeholder="String"
      value={type.name}
    />
  );
}

function Value({ id, type, value }) {
  if (type.children) return null;

  if (type.type === 'Boolean')
    return (
      <Switch
        sx={{ flex: 1 }}
        onLabel="true"
        offLabel="false"
        size="xl"
        checked={value === 'true'}
        onChange={(event) =>
          dispatch(ACTION.INPUT_UPDATE, {
            id,
            value: event.currentTarget.checked.toString(),
          })
        }
      />
    );

  const props = { value, placeholder: type.type };
  if (type.type === 'Integer' || type.type === 'Float') {
    props.type = 'number';
    let range;
    if (type.min != null) {
      props.min = type.min;
      range = `[${props.min}, `;
    } else range = '(-∞, ';
    if (type.max != null) {
      props.max = type.max;
      range += props.max + ']';
    } else range += '∞)';
    props.placeholder += ` in ${range}`;

    if (type.type === 'Float' && type.min != null && type.max != null)
      props.step = (type.max - type.min) / 100;

    if (parseFloat(value) < type.min || parseFloat(value) > type.max)
      props.error = `Must be within ${range}`;
  }

  return (
    <TextInput
      sx={{ flex: 1 }}
      onChange={(event) =>
        dispatch(ACTION.INPUT_UPDATE, {
          id,
          value: event.currentTarget.value,
        })
      }
      {...props}
    />
  );
}

function Input({ id, value, types, type }) {
  return (
    <>
      <Box sx={{ display: 'flex', gap: '5px' }}>
        <Property {...{ id, type, types }} />
        <Value {...{ id, type, value }} />
        <ActionIcon
          sx={{ marginTop: 5 }}
          onClick={() => dispatch(ACTION.INPUT_REMOVE, id)}
        >
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
  const remainingTypes =
    typeof type === 'string'
      ? null
      : Object.fromEntries(
          Object.entries(type).filter(([name]) => !usedTypes.includes(name))
        );
  const firstRemaining = remainingTypes ? Object.keys(remainingTypes)[0] : '';

  return (
    <Container sx={{ rowGap: '5px' }}>
      {inputs.map(({ id, value, type }) => (
        <Input key={id} types={remainingTypes} {...{ id, value, type }} />
      ))}
      {firstRemaining != null && (
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
