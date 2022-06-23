import React, { useContext } from 'react';
import SimpleButton from '../Buttons/SimpleButton';
import Info from './Info';
import SimpleForm from './SimpleForm';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import QueryDropdownFormn from './QueryDropDownForm';

export default function QueryInput({ id }) {
  const choices = ['yql', 'hits', 'offset', 'tracelevel'];

  const { inputs, setInputs, levelZeroParameters, childMap } =
    useContext(QueryInputContext);

  function removeRow(id) {
    const newList = inputs.filter((item) => item.id !== id);
    setInputs(newList);
  }

  const updateInput = (e) => {
    e.preventDefault();
    const fid = parseInt(e.target.id.replace('v', ''));
    const index = inputs.findIndex((element) => element.id === fid);
    inputs[index].input = e.target.value;
    setInputs(inputs);
    console.log(inputs);
  };

  const inputList = inputs.map((value, index) => {
    return (
      <div key={value.id} id={value.id} className="queryinput">
        <QueryDropdownFormn
          choices={levelZeroParameters}
          id={value.id}
        ></QueryDropdownFormn>
        <Info id={value.id} height="15" width="15" />
        <SimpleForm
          id={`v${value.id}`}
          size="30"
          onChange={updateInput}
        ></SimpleForm>
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
