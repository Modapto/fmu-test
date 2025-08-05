
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.ntnu.ihb.fmi4j.Fmi4jVariableUtils;
import no.ntnu.ihb.fmi4j.FmiStatus;
import no.ntnu.ihb.fmi4j.importer.fmi2.CoSimulationSlave;
import no.ntnu.ihb.fmi4j.importer.fmi2.Fmu;
import no.ntnu.ihb.fmi4j.modeldescription.CoSimulationModelDescription;
import no.ntnu.ihb.fmi4j.modeldescription.variables.BooleanVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.Causality;
import no.ntnu.ihb.fmi4j.modeldescription.variables.IntegerVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.RealVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.StringVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.TypedScalarVariable;
import no.ntnu.ihb.fmi4j.modeldescription.variables.Variability;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AbstractTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTest.class);
    private static final String TIME_COLUMN = "time";

    protected String fmuFilename;
    protected Fmu fmu;
    protected Map<String, String> initialValues;
    protected List<Map<String, String>> inputs;
    protected List<Map<String, Object>> expectedOutputs;

    protected AbstractTest(
            String fmuFilename,
            String initialValuesFilename,
            String inputFilename,
            String expectedOutputFilename) throws IOException, URISyntaxException, CsvException {
        this.fmuFilename = fmuFilename;
        initialValues = readPropertiesFiles(initialValuesFilename);
        fmu = Fmu.from(AbstractTest.class.getResource(fmuFilename));
        inputs = readCsvAsMaps(inputFilename);
        expectedOutputs = readCsvAsMaps(expectedOutputFilename).stream()
                .map(x -> x.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                y -> convertToCorrectFormat(y.getKey(), y.getValue()))))
                .toList();
    }


    @Test
    @Ignore
    public void testAllStepsInSingleInstance() throws IOException {
        try (CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance()) {
            init(instance);
            double t = getTime(0);
            double stepSize = 0.5;
            for (int i = 0; i < inputs.size(); i++) {
                setInputValues(instance, inputs.get(i));
                printState(instance, t);
                stepSize = computeDeltaT(i, stepSize);
                LOGGER.info("calling doStep (t={}, stepSize={})", t, stepSize);
                if (!instance.doStep(t, stepSize)) {
                    Assert.fail(String.format("doStep failed (lastStatus: %s)", instance.getLastStatus().toString()));
                }
                t += stepSize;
                Assert.assertTrue("output violation", validateOutput(instance, t));
            }
            instance.terminate();
        }
    }


    @Test
    public void testMultipleRuns() throws IOException {
        int numberOfRuns = 10;
        Boolean[][] results = new Boolean[numberOfRuns][inputs.size()];
        for (int run = 0; run < numberOfRuns; run++) {
            LOGGER.info("starting run #{} ...", run + 1);
            fmu = Fmu.from(AbstractTest.class.getResource(fmuFilename));
            try (CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance()) {
                init(instance);
                double t = getTime(0);
                double stepSize = 0.5;
                for (int i = 0; i < inputs.size(); i++) {
                    boolean failed = false;
                    setInputValues(instance, inputs.get(i));
                    printState(instance, t);
                    stepSize = computeDeltaT(i, stepSize);
                    LOGGER.info("calling doStep (t={}, stepSize={})", t, stepSize);
                    if (!instance.doStep(t, stepSize)) {
                        failed = true;
                    }
                    t += stepSize;
                    results[run][i] = !failed && validateOutput(instance, t);
                }
                instance.terminate();
            }
        }

        List<Boolean[]> failedRuns = Arrays.stream(results).filter(x -> Arrays.stream(x).anyMatch(y -> !y)).toList();
        LOGGER.info("-----------------------");
        LOGGER.info("Runs total: {}", numberOfRuns);
        LOGGER.info("Runs failed: {}", failedRuns.size());
        LOGGER.info("");
        LOGGER.info("Detailed result:");
        for (int i = 0; i < numberOfRuns; i++) {
            final Boolean[] resultPerRun = results[i];
            LOGGER.info("Run {}: {}",
                    i + 1,
                    Arrays.stream(results[i]).allMatch(x -> x)
                            ? "OK"
                            : String.format("FAILED (failed steps: %s)",
                                    IntStream.range(0, results[i].length)
                                            .filter(x -> !resultPerRun[x])
                                            .boxed()
                                            .map(x -> Integer.toString(x))
                                            .collect(Collectors.joining(", "))));

        }
        Assert.assertEquals("some runs have failed", 0, failedRuns.size());

    }


    @Test
    @Ignore
    public void testFirstStepOnly() throws IOException {
        try (CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance()) {
            init(instance);
            double t = getTime(0);
            double stepSize = 0.5;
            setInputValues(instance, inputs.get(0));
            printState(instance, t);
            stepSize = computeDeltaT(0, stepSize);
            LOGGER.info("calling doStep (t={}, stepSize={})", t, stepSize);
            if (!instance.doStep(t, stepSize)) {
                Assert.fail(String.format("doStep failed (lastStatus: %s)", instance.getLastStatus().toString()));
            }
            t += stepSize;
            Assert.assertTrue("output violation", validateOutput(instance, t));
            instance.terminate();
        }
    }


    private void init(CoSimulationSlave instance) {
        if (!instance.setupExperiment(0, 0, 0)) {
            LOGGER.warn("[WARN] setupExperiment failed");
        }
        initializeModelParameters(instance);
        if (!instance.enterInitializationMode()) {
            LOGGER.warn("[WARN] enterInitializationModel failed");
        }
        if (!instance.exitInitializationMode()) {
            LOGGER.warn("[WARN] exitInitializationModel failed");
        }
    }


    protected static List<Map<String, String>> readCsvAsMaps(String filename) throws URISyntaxException, IOException, CsvException {
        List<Map<String, String>> result = new ArrayList<>();
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(Paths.get(AbstractTest.class.getResource(filename).toURI())))) {
            String[] headers = reader.readNext();
            if (headers == null) {
                throw new IllegalArgumentException("CSV file is empty or missing headers.");
            }
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> row = new TreeMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], line[i]);
                }
                result.add(row);
            }
        }
        return result;
    }


    private static Map<String, String> readPropertiesFiles(String filename) throws URISyntaxException, IOException {
        if (Objects.isNull(filename)) {
            return Map.of();
        }
        Properties properties = new Properties();
        properties.load(Files.newBufferedReader(Paths.get(AbstractTest.class.getResource(filename).toURI())));
        return properties.stringPropertyNames().stream().collect(Collectors.toMap(x -> x, x -> properties.getProperty(x)));
    }


    protected static void printState(CoSimulationSlave instance, double t) {
        // print all variables include type/direction, etc
        LOGGER.info("----- state @ {} -----", t);
        Map<Causality, List<TypedScalarVariable>> variablesByCasualty = instance.getModelVariables().getVariables().stream()
                .filter(x -> Objects.nonNull(x.getCausality()) && x.getCausality() != Causality.INPUT && x.getCausality() != Causality.UNKNOWN)
                .collect(Collectors.groupingBy(
                        TypedScalarVariable::getCausality,
                        Collectors.mapping(
                                Function.identity(),
                                Collectors.toList())));
        variablesByCasualty.forEach((key, value) -> {
            value.sort(Comparator.comparing(TypedScalarVariable::getName));
        });

        for (var causality: variablesByCasualty.keySet()) {
            for (var variable: variablesByCasualty.get(causality)) {
                LOGGER.info("[{}] {}={}", causality, variable.getName(), Fmi4jVariableUtils.read(variable, instance).getValue().toString());
            }
        }
        LOGGER.info("-----------------------");
    }


    protected static void setInputValues(CoSimulationSlave instance, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (values.keySet().stream().noneMatch(x -> !TIME_COLUMN.equalsIgnoreCase(x))) {
            return;
        }
        LOGGER.info("setting input values...");

        for (var entry: values.entrySet()) {
            if (TIME_COLUMN.equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            LOGGER.info("{} -> {}", entry.getKey(), entry.getValue());
            setVariable(instance, entry.getKey(), entry.getValue());
        }
    }


    protected void initializeModelParameters(CoSimulationSlave instance) {
        LOGGER.info("setting initial values (causality=parameter & variability=fixed|tunable)...");
        List<TypedScalarVariable<?>> modelParameters = instance.getModelVariables().getVariables().stream()
                .filter(x -> x.getCausality() == Causality.PARAMETER)
                .filter(x -> x.getVariability() == Variability.FIXED || x.getVariability() == Variability.TUNABLE)
                .sorted(Comparator.comparing(TypedScalarVariable::getName))
                .toList();
        List<String> matchedParameters = new ArrayList<>();
        modelParameters.forEach(x -> {
            if (initialValues.containsKey(x.getName())) {
                LOGGER.info("{} --> {}", x.getName(), initialValues.get(x.getName()));
                setVariable(instance, x.getName(), initialValues.get(x.getName()));
                matchedParameters.add(x.getName());
            }
            else {
                LOGGER.info("{} = {} (default)", x.getName(), x.getStart());
            }
        });
        initialValues.keySet().stream()
                .sorted()
                .filter(x -> !matchedParameters.contains(x))
                .forEach(x -> LOGGER.info("parameter does not exist: {}", x));
        if (modelParameters.isEmpty() && initialValues.isEmpty()) {
            LOGGER.info("   [model does not have any parameters]");
        }
    }


    protected static void setVariable(CoSimulationSlave instance, String name, String value) {
        CoSimulationModelDescription description = instance.getModelDescription();
        TypedScalarVariable fmuVariable = description.getVariableByName(name);
        if (fmuVariable instanceof IntegerVariable) {
            instance.writeInteger(
                    new long[] {
                            fmuVariable.getValueReference()
                    },
                    new int[] {
                            (int) Double.parseDouble(value)
                    });
        }
        else if (fmuVariable instanceof BooleanVariable) {
            instance.writeBoolean(
                    new long[] {
                            fmuVariable.getValueReference()
                    },
                    new boolean[] {
                            Boolean.parseBoolean(value)
                    });
        }
        else if (fmuVariable instanceof RealVariable) {
            instance.writeReal(
                    new long[] {
                            fmuVariable.getValueReference()
                    },
                    new double[] {
                            Double.parseDouble(value)
                    });
        }
        else if (fmuVariable instanceof StringVariable) {
            instance.writeString(
                    new long[] {
                            fmuVariable.getValueReference()
                    },
                    new String[] {
                            value
                    });
        }

        else {
            throw new IllegalArgumentException("unsupported type: " + fmuVariable.getClass().getName());
        }
        if (!instance.getLastStatus().isOK()) {
            throw new RuntimeException(String.format(String.format("Setting input value on FMU failed (name: %s)", name)));
        }
    }


    private double computeDeltaT(int stepNumber, double prevDeltaT) {
        if (stepNumber >= inputs.size() - 1) {
            return prevDeltaT;
        }
        return getTime(stepNumber + 1) - getTime(stepNumber);
    }


    private double getTime(int stepNumber) {
        return Double.parseDouble(inputs.get(stepNumber).get(TIME_COLUMN));
    }


    protected boolean validateOutput(CoSimulationSlave instance, double t) {
        Optional<Map<String, Object>> expectedOutput = expectedOutputs.stream()
                .filter(x -> (double) x.getOrDefault(TIME_COLUMN, -1) == t)
                .findFirst();
        if (expectedOutput.isEmpty()) {
            return true;
        }
        LOGGER.info("validating results for t={} ...", t);
        return validateOutput(instance, expectedOutput.get());
    }


    protected boolean validateOutput(CoSimulationSlave instance, Map<String, Object> expectedOutput) {
        return expectedOutput.entrySet().stream()
                .sorted((Map.Entry<String, Object> o1, Map.Entry<String, Object> o2) -> o1.getKey().compareTo(o2.getKey()))
                .filter(x -> !TIME_COLUMN.equalsIgnoreCase(x.getKey()))
                .map(x -> validateOutput(instance, x.getKey(), x.getValue()))
                // needed so that validation runs for a variables and does not stop with first invalid variable
                .toList()
                .stream()
                .allMatch(x -> x);
    }


    protected boolean validateOutput(CoSimulationSlave instance, String variableName, Object value) {
        var valueRead = Fmi4jVariableUtils.read(instance.getModelVariables().getByName(variableName), instance);
        boolean result = Objects.equals(FmiStatus.OK, valueRead.getStatus())
                && Objects.equals(value, valueRead.getValue());
        LOGGER.info("{}={}? {}", variableName, value, result ? "OK" : String.format("FAILED (acutal: %s)", valueRead.getValue()));
        return result;
    }


    private Object convertToCorrectFormat(String variableName, String value) {
        if (TIME_COLUMN.equalsIgnoreCase(variableName)) {
            return Double.valueOf(value);
        }
        TypedScalarVariable<?> variable = fmu.getModelDescription().getModelVariables().getByName(variableName);
        switch (variable) {
            case IntegerVariable var -> {
                return (int) Double.parseDouble(value);
            }
            case BooleanVariable var -> {
                return Boolean.valueOf(value);
            }
            case RealVariable var -> {
                return Double.valueOf(value);
            }
            case StringVariable var -> {
                return value;
            }
            default -> {
                throw new IllegalArgumentException(String.format("unsupported variable to for expected output value '%s': %s", variableName, variable.getClass().getName()));
            }
        }       
    }

}
