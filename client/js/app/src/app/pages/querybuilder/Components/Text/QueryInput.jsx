import React, { useContext } from 'react';
import SimpleButton from '../Buttons/SimpleButton';
import Info from './Info';
import SimpleForm from './SimpleForm';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import QueryDropdownFormn from './QueryDropDownForm';

export default function QueryInput() {
  const { inputs, setInputs, levelZeroParameters, childMap } =
    useContext(QueryInputContext);

  function removeRow(id) {
    const newList = inputs.filter((item) => item.id !== id);
    setInputs(newList);
  }

  const updateInput = (e) => {
    e.preventDefault();
    const fid = parseInt(e.target.id.replace('v', ''));
    const newInputs = inputs.slice();
    const index = newInputs.findIndex((element) => element.id === fid);
    newInputs[index].input = e.target.value;
    setInputs(newInputs);
  };

  const setPlaceholder = (id) => {
    try {
      const index = inputs.findIndex((element) => element.id === id);
      const key = inputs[index].type;
      return levelZeroParameters[key].type;
    } catch (error) {
      console.log(error);
    }
  };

  const inputList = inputs.map((value) => {
    return (
      <div key={value.id} id={value.id} className="queryinput">
        <QueryDropdownFormn
          choices={levelZeroParameters}
          id={value.id}
        ></QueryDropdownFormn>
        <Info id={value.id} height="15" width="15" />
        {value.hasChildren ? (
          <SimpleButton id={`propb${value.id}`} className={'addpropsbutton'}>
            + Add property
          </SimpleButton>
        ) : (
          <SimpleForm
            id={`v${value.id}`}
            size="30"
            onChange={updateInput}
            placeholder={setPlaceholder(value.id)}
          ></SimpleForm>
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
