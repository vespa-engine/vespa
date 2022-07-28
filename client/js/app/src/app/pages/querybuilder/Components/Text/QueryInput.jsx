import React, { useContext } from 'react';
import SimpleButton from '../Buttons/SimpleButton';
import SimpleForm from './SimpleForm';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import QueryDropdownForm from './QueryDropDownForm';
import AddPropertyButton from '../Buttons/AddPropertyButton';
import QueryInputChild from './QueryInputChild';

export default function QueryInput() {
  const { inputs, setInputs, levelZeroParameters } =
    useContext(QueryInputContext);

  function removeRow(id) {
    const newList = inputs.filter((item) => item.id !== id);
    setInputs(newList);
  }

  const updateInput = (e) => {
    e.preventDefault();
    const fid = e.target.id.replace('v', '');
    const newInputs = inputs.slice();
    const index = newInputs.findIndex((element) => element.id === fid);
    newInputs[index].input = e.target.value;
    setInputs(newInputs);
  };

  const setPlaceholder = (id) => {
    try {
      const index = inputs.findIndex((element) => element.id === id);
      return inputs[index].typeof;
    } catch (error) {
      console.log(error);
    }
  };

  const inputList = inputs.map((value) => {
    return (
      <div key={value.id + value.typeof} id={value.id} className="queryinput">
        <QueryDropdownForm
          choices={levelZeroParameters}
          id={value.id}
          initial={value.type}
        />
        {value.hasChildren ? (
          <>
            <AddPropertyButton id={value.id} />
            <QueryInputChild id={value.id} />
          </>
        ) : (
          <SimpleForm
            id={`v${value.id}`}
            size="30"
            onChange={updateInput}
            placeholder={setPlaceholder(value.id)}
            initial={value.input}
          />
        )}
        <OverlayTrigger
          placement="right"
          delay={{ show: 250, hide: 400 }}
          overlay={<Tooltip id="button-tooltip">Remove row</Tooltip>}
        >
          <span>
            <SimpleButton
              id={`b${value.id}`}
              className="removeRow"
              onClick={() => removeRow(value.id)}
              children="-"
            ></SimpleButton>
          </span>
        </OverlayTrigger>
        <br />
      </div>
    );
  });

  return <>{inputList}</>;
}
