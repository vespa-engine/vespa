import { QueryInputContext } from '../Contexts/QueryInputContext';
import React, { useContext, useEffect } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';

export default function QueryDropdownFormn({ choices, id }) {
  const { inputs, setInputs, levelZeroParameters } =
    useContext(QueryInputContext);

  // Update the state of the QueryInput to reflect the chosen method
  const updateType = (e) => {
    e.preventDefault();
    const newType = e.target.value;
    const newInputs = inputs.slice();
    const index = newInputs.findIndex((element) => element.id === id);
    newInputs[index].type = newType;
    let hasChildren = levelZeroParameters[newType].hasChildren;
    newInputs[index].hasChildren = hasChildren;
    setInputs(newInputs);
  };

  // On start set the type of the first QueryInput to the first in the list of choices
  useEffect(() => {
    const newInputs = inputs.slice();
    const index = newInputs.findIndex((element) => element.id === id);
    const key = Object.keys(choices)[0];
    newInputs[index].type = choices[key].name;
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
