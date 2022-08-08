import React, { useContext } from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import AddPropertyButton from '../Buttons/AddPropertyButton';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import QueryDropdownForm from './QueryDropDownForm';
import QueryInputChild from './QueryInputChild';
import SimpleForm from './SimpleForm';
import { levelZeroParameters } from 'app/pages/querybuilder/parameters';

export default function QueryInput() {
  const { inputs, setInputs } = useContext(QueryInputContext);

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
      <div key={value.id + value.typeof} className="queryinput">
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
            <button className="removeRow" onClick={() => removeRow(value.id)}>
              -
            </button>
          </span>
        </OverlayTrigger>
        <br />
      </div>
    );
  });

  return <>{inputList}</>;
}
