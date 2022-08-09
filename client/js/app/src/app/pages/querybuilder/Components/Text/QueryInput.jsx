import React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import SimpleDropDownForm from 'app/pages/querybuilder/Components/Text/SimpleDropDownForm';
import AddPropertyButton from 'app/pages/querybuilder/Components/Buttons/AddPropertyButton';
import {
  ACTION,
  dispatch,
  useQueryBuilderContext,
} from 'app/pages/querybuilder/Components/Contexts/QueryBuilderProvider';

export default function QueryInput() {
  const inputs = useQueryBuilderContext((ctx) => ctx.query.children);
  return <Inputs inputs={inputs} />;
}

function Inputs({ inputs }) {
  return inputs.map(
    ({
      id,
      input,
      parent: {
        type: { children: siblingTypes },
      },
      type: { name, type, children },
      children: inputsChildren,
    }) => (
      <div key={id} className="queryinput">
        <SimpleDropDownForm
          onChange={({ target }) =>
            dispatch(ACTION.INPUT_UPDATE, {
              id,
              type: siblingTypes[target.value],
            })
          }
          options={siblingTypes}
          value={name}
        />
        {children ? (
          <>
            {inputsChildren && <Inputs inputs={inputsChildren} />}
            <AddPropertyButton id={id} />
          </>
        ) : (
          <input
            size="30"
            onChange={({ target }) =>
              dispatch(ACTION.INPUT_UPDATE, {
                id,
                input: target.value,
              })
            }
            placeholder={type}
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
    )
  );
}
