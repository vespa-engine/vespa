import { QueryInputContext } from '../Contexts/QueryInputContext';
import React, { useContext, useEffect } from 'react';
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
      //this is needed because substring() is exclusive the last parameter
      let iterId = id + '.';
      let currentId = iterId.slice(0, 1);
      let index = newInputs.findIndex((element) => element.id === currentId); // get the index of the root parent
      let children = newInputs[index].children;
      let parentTypes = newInputs[index].type;
      let childChoices = childMap[parentTypes];
      for (let i = 3; i < iterId.length - 2; i += 2) {
        console.log('GOT HERE');
        currentId = iterId.slice(0, i);
        index = children.findIndex((element) => element.id === currentId);
        let child = children[index];
        parentTypes = parentTypes + '_' + child.name;
        childChoices = childMap[parentTypes];
        console.log(parentTypes);
        console.log(childChoices);
        children = child.children;
      }
      index = children.findIndex(
        (element) => element.id === iterId.slice(0, iterId.length - 1)
      );
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

  // On start set the type of the first QueryInput to the first in the list of choices
  useEffect(() => {
    const newInputs = inputs.slice();
    const key = Object.keys(choices)[0];
    if (child) {
      let iterId = id + '.';
      let currentId = iterId.slice(0, 1);
      let index = newInputs.findIndex((element) => element.id === currentId);
      let children = newInputs[index].children;
      for (let i = 3; i < iterId.length - 2; i += 2) {
        currentId = iterId.slice(0, i);
        console.log(iterId);
        index = children.findIndex((element) => element.id === currentId);
        children = children[index].children;
      }
      index = children.findIndex(
        (element) => element.id === iterId.slice(0, iterId.length - 1)
      );
      children[index].type = choices[key].name;
    } else {
      const index = newInputs.findIndex((element) => element.id === id);
      newInputs[index].type = choices[key].name;
    }
    setInputs(newInputs);
  }, []);

  return (
    <SimpleDropDownForm
      id={id}
      onChange={updateType}
      choices={choices}
    ></SimpleDropDownForm>
  );
}
