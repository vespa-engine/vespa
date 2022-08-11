import React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import SimpleDropDownForm from 'app/pages/querybuilder/Components/Text/SimpleDropDownForm';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/context/query-builder-provider';

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
    <>
      {inputs.map(({ id, input, type, children }) => (
        <Input
          key={id}
          types={remainingTypes}
          {...{ id, input, type, children }}
        />
      ))}
      {firstRemaining && <AddPropertyButton id={id} type={firstRemaining} />}
    </>
  );
}

function Input({ id, input, types, type, children }) {
  return (
    <div className="queryinput">
      <SimpleDropDownForm
        onChange={({ target }) =>
          dispatch(ACTION.INPUT_UPDATE, {
            id,
            type: types[target.value],
          })
        }
        options={{ [type.name]: type, ...types }}
        value={type.name}
      />
      {children ? (
        <Inputs id={id} type={type.children} inputs={children} />
      ) : (
        <input
          size="30"
          onChange={({ target }) =>
            dispatch(ACTION.INPUT_UPDATE, {
              id,
              input: target.value,
            })
          }
          placeholder={type.type}
          value={input}
        />
      )}
      <OverlayTrigger
        placement="right"
        delay={{ show: 250, hide: 400 }}
        overlay={<Tooltip id="button-tooltip">Remove row</Tooltip>}
      >
        <span>
          <button
            className="removeRow"
            onClick={() => dispatch(ACTION.INPUT_REMOVE, id)}
          >
            -
          </button>
        </span>
      </OverlayTrigger>
      <br />
    </div>
  );
}

function AddPropertyButton({ id, type }) {
  return (
    <button
      className="addpropsbutton"
      onClick={() => dispatch(ACTION.INPUT_ADD, { id, type })}
    >
      + Add property
    </button>
  );
}
