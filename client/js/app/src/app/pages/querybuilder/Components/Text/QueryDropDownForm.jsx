import { QueryInputContext } from '../Contexts/QueryInputContext';
import React, { useContext } from 'react';
import SimpleDropDownForm from './SimpleDropDownForm';

export default function QueryDropdownForm({ choices, id, child = false }) {
  const { inputs, setInputs, levelZeroParameters, childMap } =
    useContext(QueryInputContext);

  // Update the state of the QueryInput to reflect the chosen method
  const updateType = (e) => {
    e.preventDefault();
    const newType = e.target.value;
    const newInputs = inputs.slice();
    if (child) {
      let currentId = id.substring(0, 1);
      let index = newInputs.findIndex((element) => element.id === currentId); // get the index of the root parent
      let children = newInputs[index].children;
      let parentTypes = newInputs[index].type;
      let childChoices = childMap[parentTypes];
      for (let i = 3; i < id.length; i += 2) {
        currentId = id.substring(0, i);
        index = children.findIndex((element) => element.id === currentId);
        let child = children[index];
        parentTypes = parentTypes + '_' + child.type;
        childChoices = childMap[parentTypes];
        children = child.children;
      }
      index = children.findIndex((element) => element.id === id);
      children[index].type = newType;
      children[index].hasChildren = childChoices[newType].hasChildren;
    } else {
      const index = newInputs.findIndex((element) => element.id === id);
      newInputs[index].type = newType;
      let hasChildren = levelZeroParameters[newType].hasChildren;
      newInputs[index].hasChildren = hasChildren;
    }
    setInputs(newInputs);
  };

  return (
    <SimpleDropDownForm
      id={id}
      onChange={updateType}
      choices={choices}
    ></SimpleDropDownForm>
  );
}
