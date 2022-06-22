import { QueryInputContext } from '../Contexts/QueryInputContext';
import React, { useContext } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';

export default function QueryDropdownFormn({ choices, id }) {
  const { inputs, setInputs } = useContext(QueryInputContext);

  const updateInputs = (e) => {
    e.preventDefault();
    const index = inputs.findIndex((element) => element.id === id);
    inputs[index].type = e.target.value;
    setInputs(inputs);
  };

  return (
    <SimpleDropDownForm
      id={id}
      onChange={updateInputs}
      choices={choices}
    ></SimpleDropDownForm>
  );
}
