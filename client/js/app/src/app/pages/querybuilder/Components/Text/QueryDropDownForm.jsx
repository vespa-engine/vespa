import { QueryInputContext } from '../Contexts/QueryInputContext';
import React, { useContext, useEffect } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';

export default function QueryDropdownFormn({ choices, id }) {
  const { inputs, setInputs } = useContext(QueryInputContext);

  var stringType = ['select'];
  var booleanType = ['nocachewrite'];
  var floatType = ['timeout'];

  const updateType = (e) => {
    e.preventDefault();
    const index = inputs.findIndex((element) => element.id === id);
    inputs[index].type = e.target.value;
    setInputs(inputs);
  };

  useEffect(() => {
    const index = inputs.findIndex((element) => element.id === id);
    inputs[index].type = choices[0];
    setInputs(inputs);
  }, []);

  return (
    <SimpleDropDownForm
      id={id}
      onChange={updateType}
      choices={choices}
    ></SimpleDropDownForm>
  );
}
