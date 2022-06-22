import { QueryInputContext } from '../Contexts/QueryInputContext';
import React, { useContext } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';

export default function QueryDropdownFormn({ choices, id }) {
  const { inputs, setInputs } = useContext(QueryInputContext);

  const updateType = (e) => {
    e.preventDefault();
    const index = inputs.findIndex((element) => element.id === id);
    inputs[index].type = e.target.value;
    setInputs(inputs);
  };

  //TODO: Try to move this into SimpleDropDownForm

  return (
    <SimpleDropDownForm
      id={id}
      onChange={updateType}
      choices={choices}
    ></SimpleDropDownForm>
  );
}
