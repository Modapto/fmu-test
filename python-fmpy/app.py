
from fmpy import read_model_description, extract
from fmpy.fmi2 import FMU2Slave
import pandas as pd
import numpy as np


def load_data(input_file, validation_file, init_file):
    input_data = pd.read_csv(input_file)
    validation_data = pd.read_csv(validation_file)
    init_data = {}
    if init_file is not None:
        with open(init_file, 'r') as file:
            for line in file:
                line = line.strip()
                if line and not line.startswith('#'):
                    key, value = line.split('=', 1)
                    init_data[key.strip()] = value.strip()    
    return input_data, validation_data, init_data

def convert(type, value):    
    if type == 'Real':
        return float(value)
    elif type == 'Integer':
        return int(value)
    elif type == 'Boolean':
        return str(value).lower() in ['true', '1']
    elif type == 'String':
        return str(value)
    else:
        print(f'Unknown type for variable: {variable.name}')

def validate_results(instance, model_description, validation_data, t):
    if t in validation_data['time'].values:
        expected_row = validation_data[validation_data['time'] == t].iloc[0]
        print(f'Validating results for t={t}:')
        for variable in expected_row.index[1:]:  # Skip the first column (time)            
            type = variable_by_name(model_description, variable).type;
            expected_value = convert(type, expected_row[variable])
            actual_value = convert(type, getValue(instance, model_description, variable))
            if actual_value == expected_value:
                print(f'{variable} = {str(expected_value)} ? OK')
            else:
                print(f'{variable} = {expected_value} ? FAILED (actual: {actual_value})')
    else:
        print(f'No validation entry for t={t}, skipping validation.')

def setValues(instance, model_description, inputs):    
    for key, value in inputs.items():
        setValue(instance, model_description, key, value)
        print(f'{key} -> {value}')
		
def setValue(instance, model_description, name, value):
    variable = variable_by_name(model_description, name)
    if variable.type == 'Real':
        instance.setReal([variable.valueReference], [convert(variable.type, value)])
    elif variable.type == 'Integer':
        instance.setInteger([variable.valueReference], [convert(variable.type, value)])
    elif variable.type == 'Boolean':
        instance.setBoolean([variable.valueReference], [convert(variable.type, value)])
    elif variable.type == 'String':
        instance.setString([variable.valueReference], [convert(variable.type, value)])
    else:
        print(f'Unknown type for variable: {variable.name}')

def getValue(instance, model_description, name):
    variable = variable_by_name(model_description, name)
    if variable.type == 'Real':
        return instance.getReal([variable.valueReference])[0]
    elif variable.type == 'Integer':
        return instance.getInteger([variable.valueReference])[0]
    elif variable.type == 'Boolean':
        return True if instance.getBoolean([variable.valueReference])[0] != 0 else 'False'
    elif variable.type == 'String':
        return instance.getString([variable.valueReference])[0].decode('utf-8')

def variable_by_name(model_description, name):
    for variable in model_description.modelVariables:
        if variable.name == name:
            return variable
    return None

def print_state(instance, model_description):
    for variable in model_description.modelVariables:
        if (variable.causality != 'input'):
            print(f'[{variable.causality}]{variable.name}={getValue(instance, model_description, variable.name)}')

def main(fmu_file, input_file, validation_file, init_file=None):
    input_data, validation_data, init_data = load_data(input_file, validation_file, init_file)
    model_description = read_model_description(fmu_file)
    unzipdir = extract(fmu_file)

    for i in range(10):
        print(f'starting run #{i+1}...')
        t = 0.0
        stepSize = 0.5
        instance = FMU2Slave(guid=model_description.guid,
                        unzipDirectory=unzipdir,
                        modelIdentifier=model_description.coSimulation.modelIdentifier,
                        instanceName='instance1')

        instance.instantiate()
        instance.setupExperiment(startTime=0)
        print(f'setting initial values...')
        setValues(instance, model_description, init_data)
            
        instance.enterInitializationMode()
        instance.exitInitializationMode()
        print(f'----- initial state -----')
        print_state(instance, model_description)
        print(f'-----------------------')

        for index, row in input_data.iterrows():       
            inputs = {variable: row[variable] for variable in input_data.columns[1:]}  # Skip the first column (time)        
            print(f'setting input values...')
            setValues(instance, model_description, inputs)

            if index < len(input_data) - 1:
                stepSize = input_data.iloc[index + 1]['time'] - row['time']
            else:
                stepSize = 0.5 

            print(f'----- state @ {t} -----')
            print_state(instance, model_description)
            print(f'-----------------------')
		
            print(f'Calling doStep(t={t}, stepSize={stepSize})')
            instance.doStep(currentCommunicationPoint=t, communicationStepSize=stepSize)		

            t += stepSize

            validate_results(instance, model_description, validation_data, t)

        instance.terminate()
        instance.freeInstance()

if __name__ == "__main__":
    #main('Feedthrough.fmu', 'Feedthrough_in.csv', 'Feedthrough_out.csv')
    #main('eks.fmu', 'eks_in.csv', 'eks_out.csv', 'eks.properties')
    main('modapto-ecm-electric.fmu', 'modapto-ecm-electric_in.csv', 'modapto-ecm-electric_out.csv', 'modapto-ecm-electric.properties')
    #main('modapto-ecm-mechanic.fmu', 'modapto-ecm-mechanic_in.csv', 'modapto-ecm-mechanic_out.csv', 'modapto-ecm-mechanic.properties')