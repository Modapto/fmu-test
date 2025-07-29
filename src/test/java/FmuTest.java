
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FmuTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(FmuTest.class);
    private static final String FILE_EKS = "eks.fmu";
    private static final String FILE_BOUNCING_BALL = "BouncingBall.fmu";
    private static final String FILE_FEEDTHROUGH = "Feedthrough.fmu";
    private static final String FILE_ECM_MECHANIC = "modapto-ecm-mechanic.fmu";
    private static final String FILE_ECM_MECHANIC_INPUT = "modapto-ecm-mechanic_in.csv";
    private static final Map<String, String> INPUT_EKS = new HashMap<>();
    private static final Map<String, String> INPUT_FEEDTHROUGH = new HashMap<>();

    @Test
    public void testSingleInstanceSingleStepRunOnceEKS() throws IOException {
        Fmu fmu = loadFmuEKS();
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
        instance.simpleSetup();
        setInputValues(instance, INPUT_EKS);
        double t = 0;
        double stepSize = 0.5;
        LOGGER.info("[before doStep t={}]", t);
        printInstanceState(instance);
        if (!instance.doStep(t, stepSize)) {
            LOGGER.info("[step failed t={}]", t);
            printInstanceState(instance);
            Assert.fail(instance.getLastStatus().toString());
        }
        LOGGER.info("[after doStep t={}]", t);
        printInstanceState(instance);
        assertSuccessEKS(instance);
        instance.terminate();
        instance.close();
        fmu.close();
    }


    @Test
    public void testSingleInstanceMultipleStepRunOnceEKS() throws IOException {
        Fmu fmu = loadFmuEKS();
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
        instance.simpleSetup();
        setInputValues(instance, INPUT_EKS);
        int steps = 2;
        double t = 0;
        double stepSize = 0.5;
        int i = 0;
        while (i < steps) {
            LOGGER.info("[before doStep t={}]", t);
            printInstanceState(instance);
            if (!instance.doStep(t, stepSize)) {
                LOGGER.info("[step failed t={}]", t);
                printInstanceState(instance);
                break;
            }
            LOGGER.info("[after doStep t={}]", t);
            printInstanceState(instance);
            t += stepSize;
            LOGGER.info("[after increase t={}]", t);
            printInstanceState(instance);
            assertSuccessEKS(instance);
            i++;
        }
        instance.terminate();
        instance.close();
        fmu.close();
    }


    @Test
    public void testSingleInstanceMultipleStepRunOnceFeedthrough() throws IOException {
        Fmu fmu = loadFmuFeedthrough();
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
        instance.simpleSetup();
        setInputValues(instance, INPUT_FEEDTHROUGH);
        int steps = 2;
        double t = 0;
        double stepSize = 0.5;
        int i = 0;
        while (i < steps) {
            LOGGER.info("[before doStep t={}]", t);
            printInstanceState(instance);
            if (!instance.doStep(t, stepSize)) {
                LOGGER.info("[step failed t={}]", t);
                printInstanceState(instance);
                break;
            }
            LOGGER.info("[after doStep t={}]", t);
            printInstanceState(instance);
            t += stepSize;
            LOGGER.info("[after increase t={}]", t);
            printInstanceState(instance);
            assertSuccessFeedthrough(instance);
            i++;
        }
        instance.terminate();
        instance.close();
        fmu.close();
    }


    @Test
    public void testMultipleInstanceSingleStepRunOnceEKS() throws IOException {
        Fmu fmu = loadFmuEKS();
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance("foo");
        instance.simpleSetup();
        setInputValues(instance, INPUT_EKS);
        double t = 0;
        double stepSize = 0.5;
        LOGGER.info("[before doStep t={}]", t);
        printInstanceState(instance);
        if (!instance.doStep(t, stepSize)) {
            LOGGER.info("[step failed t={}]", t);
            printInstanceState(instance);
            Assert.fail(instance.getLastStatus().toString());
        }
        LOGGER.info("[after doStep t={}]", t);
        printInstanceState(instance);
        assertSuccessEKS(instance);
        instance.terminate();
        instance.close();
        t += stepSize;
        instance = fmu.asCoSimulationFmu().newInstance("bar");
        instance.simpleSetup();
        setInputValues(instance, INPUT_EKS);

        LOGGER.info("[before doStep t={}]", t);
        printInstanceState(instance);
        if (!instance.doStep(t, stepSize)) {
            LOGGER.info("[step failed t={}]", t);
            printInstanceState(instance);
            Assert.fail(instance.getLastStatus().toString());
        }
        LOGGER.info("[after doStep t={}]", t);
        printInstanceState(instance);
        assertSuccessEKS(instance);
        instance.terminate();
        instance.close();
        fmu.close();
    }


    @Test
    public void testSingleInstanceSingleStepRunMultipleEKS() throws IOException {
        int runs = 5;
        Boolean[] results = new Boolean[runs];
        for (int i = 1; i <= runs; i++) {
            LOGGER.info("starting run {}...", i);
            Fmu fmu = loadFmuEKS();
            CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
            instance.simpleSetup();
            setInputValues(instance, INPUT_EKS);
            double t = 0;
            double stepSize = 0.5;
            LOGGER.info("[before doStep t={}]", t);
            printInstanceState(instance);
            if (!instance.doStep(t, stepSize)) {
                LOGGER.info("[step failed t={}]", t);
                printInstanceState(instance);
                Assert.fail(instance.getLastStatus().toString());
            }
            LOGGER.info("[after doStep t={}]", t);
            printInstanceState(instance);
            boolean success = checkSuccessEKS(instance);
            results[i - 1] = success;
            LOGGER.info("--> success: {}", success);
            instance.terminate();
            instance.close();
            fmu.close();
        }
        LOGGER.info("----- SUMMARY -----");
        for (int i = 0; i < results.length; i++) {
            LOGGER.info("run {}: {}", i + 1, results[i] ? "success" : "FAILED");
        }
        long countFailed = Arrays.stream(results).filter(x -> x == false).count();
        LOGGER.info("runs failed: {}", countFailed);
        Assert.assertEquals(0, countFailed);
    }


    @Test
    public void testSingleInstanceSingleStepRunMultipleFeedthrough() throws IOException {
        int runs = 5;
        Boolean[] results = new Boolean[runs];
        for (int i = 1; i <= runs; i++) {
            LOGGER.info("starting run {}...", i);
            Fmu fmu = loadFmuFeedthrough();
            CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
            instance.simpleSetup();
            setInputValues(instance, INPUT_FEEDTHROUGH);
            double t = 0;
            double stepSize = 0.5;
            LOGGER.info("[before doStep t={}]", t);
            printInstanceState(instance);
            if (!instance.doStep(t, stepSize)) {
                LOGGER.info("[step failed t={}]", t);
                printInstanceState(instance);
                Assert.fail(instance.getLastStatus().toString());
            }
            LOGGER.info("[after doStep t={}]", t);
            printInstanceState(instance);
            boolean success = checkSuccessFeedthrough(instance);
            results[i - 1] = success;
            LOGGER.info("--> success: {}", success);
            instance.terminate();
            instance.close();
            fmu.close();
        }
        LOGGER.info("----- SUMMARY -----");
        for (int i = 0; i < results.length; i++) {
            LOGGER.info("run {}: {}", i + 1, results[i] ? "success" : "FAILED");
        }
        long countFailed = Arrays.stream(results).filter(x -> x == false).count();
        LOGGER.info("runs failed: {}", countFailed);
        Assert.assertEquals(0, countFailed);
    }


    @Test
    public void testSingleInstanceSingleStepRunOnceBouncingBall() throws IOException {
        Fmu fmu = loadFmuBouncingBall();
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
        instance.simpleSetup();
        double t = 0;
        double stepSize = 0.5;
        LOGGER.info("[before doStep t={}]", t);
        printInstanceState(instance);
        if (!instance.doStep(t, stepSize)) {
            LOGGER.info("[step failed t={}]", t);
            printInstanceState(instance);
            Assert.fail(instance.getLastStatus().toString());
        }
        LOGGER.info("[after doStep t={}]", t);
        printInstanceState(instance);
        assertValue(instance, "h", 0.13560068699999941);
        assertValue(instance, "v", 2.64968099999999);
        instance.terminate();
        instance.close();
        fmu.close();
    }


    private static List<Map<String, String>> readCsvAsMaps(String filename) throws URISyntaxException, IOException, CsvException {
        List<Map<String, String>> result = new ArrayList<>();
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(Paths.get(FmuTest.class.getResource(filename).toURI())))) {
            String[] headers = reader.readNext();
            if (headers == null) {
                throw new IllegalArgumentException("CSV file is empty or missing headers.");
            }
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], line[i]);
                }
                result.add(row);
            }
        }
        return result;
    }


    @Test
    @Ignore("fails because input max_current[5] is not defined")
    public void testEcmMachanic() throws IOException, URISyntaxException, CsvException {
        List<Map<String, String>> input = readCsvAsMaps(FILE_ECM_MECHANIC_INPUT);
        Fmu fmu = Fmu.from(FmuTest.class.getResource(FILE_ECM_MECHANIC));
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
        instance.simpleSetup();
        double stepSize = 0;
        for (int i = 0; i < input.size(); i++) {
            var inputPerStep = input.get(i);
            double t = Double.parseDouble(input.get(i).get("time"));
            if (i < input.size() - 1) {
                stepSize = Double.parseDouble(input.get(i + 1).get("time")) - t;
            }
            inputPerStep.remove("time");
            setInputValues(instance, inputPerStep);
            LOGGER.info("[before doStep t={}]", t);
            printInstanceState(instance);
            if (!instance.doStep(t, stepSize)) {
                LOGGER.info("[step failed t={}]", t);
                printInstanceState(instance);
                Assert.fail(instance.getLastStatus().toString());
            }
            LOGGER.info("[after doStep t={}]", t);
            printInstanceState(instance);
        }
        instance.terminate();
        instance.close();
        fmu.close();
    }


    @Test
    public void testSingleInstanceMultipleStepRunOnceBouncingBall() throws IOException {
        Fmu fmu = loadFmuBouncingBall();
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
        instance.simpleSetup();
        int steps = 2;
        double t = 0;
        double stepSize = 0.5;
        int i = 0;
        while (i < steps) {
            LOGGER.info("[before doStep t={}]", t);
            printInstanceState(instance);
            if (!instance.doStep(t, stepSize)) {
                LOGGER.info("[step failed t={}]", t);
                printInstanceState(instance);
                break;
            }
            LOGGER.info("[after doStep t={}]", t);
            printInstanceState(instance);
            t += stepSize;
            LOGGER.info("[after increase t={}]", t);
            printInstanceState(instance);
            i++;
        }
        assertValue(instance, "h", 0.23664368699999475);
        assertValue(instance, "v", -2.255319000000016);
        instance.terminate();
        instance.close();
        fmu.close();
    }


    @Test
    public void testSingleInstanceMultipleStepRunOnceDahlquist() throws IOException {
        Fmu fmu = Fmu.from(FmuTest.class.getResource("Dahlquist.fmu"));
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
        instance.simpleSetup();
        int steps = 2;
        double t = 0;
        double stepSize = 0.5;
        int i = 0;
        while (i < steps) {
            LOGGER.info("[before doStep t={}]", t);
            printInstanceState(instance);
            if (!instance.doStep(t, stepSize)) {
                LOGGER.info("[step failed t={}]", t);
                printInstanceState(instance);
                break;
            }
            LOGGER.info("[after doStep t={}]", t);
            printInstanceState(instance);
            t += stepSize;
            LOGGER.info("[after increase t={}]", t);
            printInstanceState(instance);
            i++;
        }
        assertValue(instance, "x", 0.3486784401);
        instance.terminate();
        instance.close();
        fmu.close();
    }


    @Test
    public void testMultipleInstanceSingleStepRunOnceBouncingBall() throws IOException {
        Fmu fmu = loadFmuBouncingBall();
        CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance("foo");
        instance.simpleSetup();
        double t = 0;
        double stepSize = 0.5;
        LOGGER.info("[before doStep t={}]", t);
        printInstanceState(instance);
        if (!instance.doStep(t, stepSize)) {
            LOGGER.info("[step failed t={}]", t);
            printInstanceState(instance);
            Assert.fail(instance.getLastStatus().toString());
        }
        LOGGER.info("[after doStep t={}]", t);
        printInstanceState(instance);
        assertValue(instance, "h", 0.13560068699999941);
        assertValue(instance, "v", 2.64968099999999);
        instance.terminate();
        instance.close();
        t += stepSize;
        instance = fmu.asCoSimulationFmu().newInstance("bar");
        instance.simpleSetup();
        LOGGER.info("[before doStep t={}]", t);
        printInstanceState(instance);
        if (!instance.doStep(t, stepSize)) {
            LOGGER.info("[step failed t={}]", t);
            printInstanceState(instance);
            Assert.fail(instance.getLastStatus().toString());
        }
        LOGGER.info("[after doStep t={}]", t);
        printInstanceState(instance);
        assertValue(instance, "h", 0.23664368699999475);
        assertValue(instance, "v", -2.255319000000016);
        instance.terminate();
        instance.close();
        fmu.close();
    }


    @Test
    public void testSingleInstanceSingleStepRunMultipleBouncingBall() throws IOException {
        int runs = 5;
        Boolean[] results = new Boolean[runs];
        for (int i = 1; i <= runs; i++) {
            LOGGER.info("starting run {}...", i);
            Fmu fmu = loadFmuBouncingBall();
            CoSimulationSlave instance = fmu.asCoSimulationFmu().newInstance();
            instance.simpleSetup();
            double t = 0;
            double stepSize = 0.5;
            LOGGER.info("[before doStep t={}]", t);
            printInstanceState(instance);
            if (!instance.doStep(t, stepSize)) {
                LOGGER.info("[step failed t={}]", t);
                printInstanceState(instance);
                Assert.fail(instance.getLastStatus().toString());
            }
            LOGGER.info("[after doStep t={}]", t);
            printInstanceState(instance);
            boolean success = checkValue(instance, "h", 0.13560068699999941) &&
                    checkValue(instance, "v", 2.64968099999999);
            results[i - 1] = success;
            LOGGER.info("--> success: {}", success);
            instance.terminate();
            instance.close();
            fmu.close();
        }
        LOGGER.info("----- SUMMARY -----");
        for (int i = 0; i < results.length; i++) {
            LOGGER.info("run {}: {}", i + 1, results[i] ? "success" : "FAILED");
        }
        long countFailed = Arrays.stream(results).filter(x -> x == false).count();
        LOGGER.info("runs failed: {}", countFailed);
        Assert.assertEquals(0, countFailed);
    }


    private void assertSuccessEKS(CoSimulationSlave instance) {
        assertValue(instance, "measurement_state", 1);
    }


    private void assertSuccessFeedthrough(CoSimulationSlave instance) {
        assertValue(instance, "Float64_continuous_output", Double.parseDouble(INPUT_FEEDTHROUGH.get("Float64_continuous_input")));
        assertValue(instance, "Float64_discrete_output", Double.parseDouble(INPUT_FEEDTHROUGH.get("Float64_discrete_input")));
        assertValue(instance, "Int32_output", Integer.parseInt(INPUT_FEEDTHROUGH.get("Int32_input")));
        assertValue(instance, "Boolean_output", Boolean.parseBoolean(INPUT_FEEDTHROUGH.get("Boolean_input")));
        assertValue(instance, "String_output", INPUT_FEEDTHROUGH.get("String_input"));
    }


    private boolean checkSuccessFeedthrough(CoSimulationSlave instance) {
        return checkValue(instance, "Float64_continuous_output", Double.parseDouble(INPUT_FEEDTHROUGH.get("Float64_continuous_input")))
                && checkValue(instance, "Float64_discrete_output", Double.parseDouble(INPUT_FEEDTHROUGH.get("Float64_discrete_input")))
                && checkValue(instance, "Int32_output", Integer.parseInt(INPUT_FEEDTHROUGH.get("Int32_input")))
                && checkValue(instance, "Boolean_output", Boolean.parseBoolean(INPUT_FEEDTHROUGH.get("Boolean_input")))
                && checkValue(instance, "String_output", INPUT_FEEDTHROUGH.get("String_input"));
    }


    private boolean checkSuccessEKS(CoSimulationSlave instance) {
        return checkValue(instance, "measurement_state", 1);
    }


    private <T> void assertValue(CoSimulationSlave instance, String variableName, T value) {
        var valueRead = Fmi4jVariableUtils.read(instance.getModelVariables().getByName(variableName), instance);
        Assert.assertEquals(FmiStatus.OK, valueRead.getStatus());
        Assert.assertEquals(value, valueRead.getValue());
    }


    private <T> boolean checkValue(CoSimulationSlave instance, String variableName, T value) {
        var valueRead = Fmi4jVariableUtils.read(instance.getModelVariables().getByName(variableName), instance);
        return Objects.equals(FmiStatus.OK, valueRead.getStatus())
                && Objects.equals(value, valueRead.getValue());
    }


    private static void printInstanceState(CoSimulationSlave instance) {
        //        LOGGER.info("DO_STEP_STATUS: {}", instance.getStatus(FmiStatusKind.DO_STEP_STATUS));
        //        LOGGER.info("LAST_SUCCESSFUL_TIME: {}", instance.getStatus(FmiStatusKind.LAST_SUCCESSFUL_TIME));
        //        LOGGER.info("PENDING_STATUS: {}", instance.getStatus(FmiStatusKind.PENDING_STATUS));
        //        LOGGER.info("TERMINATED: {}", instance.getStatus(FmiStatusKind.TERMINATED));
        //        LOGGER.info("");
        instance.getModelVariables().getByCausality(Causality.OUTPUT).stream()
                .forEach(x -> LOGGER.info("{}={}", x.getName(), Fmi4jVariableUtils.read(x, instance).getValue().toString()));
        LOGGER.info("----------------------------------------");
    }


    @BeforeClass
    public static void init() {
        INPUT_EKS.put("robot.axis_val[0]", "1.0");
        INPUT_EKS.put("robot.axis_val[1]", "0.0");
        INPUT_EKS.put("robot.axis_val[2]", "0.0");
        INPUT_EKS.put("robot.axis_val[3]", "0.0");
        INPUT_EKS.put("robot.axis_val[4]", "0.0");
        INPUT_EKS.put("robot.axis_val[5]", "0.0");
        INPUT_EKS.put("robot.axis_vel[0]", "1.0");
        INPUT_EKS.put("robot.axis_vel[1]", "1.0");
        INPUT_EKS.put("robot.axis_vel[2]", "1.0");
        INPUT_EKS.put("robot.axis_vel[3]", "1.0");
        INPUT_EKS.put("robot.axis_vel[4]", "1.0");
        INPUT_EKS.put("robot.axis_vel[5]", "1.0");
        INPUT_EKS.put("robot.axis_acc[0]", "1.0");
        INPUT_EKS.put("robot.axis_acc[1]", "1.0");
        INPUT_EKS.put("robot.axis_acc[2]", "1.0");
        INPUT_EKS.put("robot.axis_acc[3]", "1.0");
        INPUT_EKS.put("robot.axis_acc[4]", "1.0");
        INPUT_EKS.put("robot.axis_acc[5]", "1.0");
        INPUT_EKS.put("robot.axis_jerk[0]", "1.0");
        INPUT_EKS.put("robot.axis_jerk[1]", "1.0");
        INPUT_EKS.put("robot.axis_jerk[2]", "1.0");
        INPUT_EKS.put("robot.axis_jerk[3]", "1.0");
        INPUT_EKS.put("robot.axis_jerk[4]", "1.0");
        INPUT_EKS.put("robot.axis_jerk[5]", "1.0");
        INPUT_EKS.put("start_measurement", "true");
        INPUT_EKS.put("stop_measurement", "false");
        INPUT_EKS.put("reset_measurement", "false");
        INPUT_EKS.put("current[0]", "10");
        INPUT_EKS.put("current[1]", "10");
        INPUT_EKS.put("current[2]", "10");
        INPUT_EKS.put("current[3]", "10");
        INPUT_EKS.put("current[4]", "10");
        INPUT_EKS.put("current[5]", "10");
        INPUT_EKS.put("robot.press_hem_roller", "2.6");
        INPUT_EKS.put("robot.t_move", "1.0");
        INPUT_EKS.put("robot.t_weld", "1.0");
        INPUT_EKS.put("robot.toolMass", "10.0");
        INPUT_EKS.put("max_current[0]", "100.0");
        INPUT_EKS.put("max_current[1]", "100.0");
        INPUT_EKS.put("max_current[2]", "100.0");
        INPUT_EKS.put("max_current[3]", "100.0");
        INPUT_EKS.put("max_current[4]", "100.0");
        INPUT_EKS.put("max_current[5]", "100.0");
        INPUT_EKS.put("use_electric", "true");
        INPUT_EKS.put("robot.version", "foo");

        INPUT_FEEDTHROUGH.put("Float64_continuous_input", "1.1");
        INPUT_FEEDTHROUGH.put("Float64_discrete_input", "1.2");
        INPUT_FEEDTHROUGH.put("Int32_input", "2");
        INPUT_FEEDTHROUGH.put("Boolean_input", "true");
        INPUT_FEEDTHROUGH.put("String_input", "Hello, world!");
    }


    private Fmu loadFmuEKS() throws IOException {
        return Fmu.from(FmuTest.class.getResource(FILE_EKS));
    }


    private Fmu loadFmuBouncingBall() throws IOException {
        return Fmu.from(FmuTest.class.getResource(FILE_BOUNCING_BALL));
    }


    private Fmu loadFmuFeedthrough() throws IOException {
        return Fmu.from(FmuTest.class.getResource(FILE_FEEDTHROUGH));
    }


    private static void setInputValues(CoSimulationSlave instance, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        LOGGER.info("setting values...");
        CoSimulationModelDescription modelDescription = instance.getModelDescription();
        for (var entry: values.entrySet()) {
            LOGGER.info("{} -> {}", entry.getKey(), entry.getValue());
            TypedScalarVariable fmuVariable = modelDescription.getVariableByName(entry.getKey());
            if (fmuVariable instanceof IntegerVariable) {
                instance.writeInteger(
                        new long[] {
                                fmuVariable.getValueReference()
                        },
                        new int[] {
                                Integer.parseInt(entry.getValue())
                        });
            }
            else if (fmuVariable instanceof BooleanVariable) {
                instance.writeBoolean(
                        new long[] {
                                fmuVariable.getValueReference()
                        },
                        new boolean[] {
                                Boolean.parseBoolean(entry.getValue())
                        });
            }
            else if (fmuVariable instanceof RealVariable) {
                instance.writeReal(
                        new long[] {
                                fmuVariable.getValueReference()
                        },
                        new double[] {
                                Double.parseDouble(entry.getValue())
                        });
            }
            else if (fmuVariable instanceof StringVariable) {
                instance.writeString(
                        new long[] {
                                fmuVariable.getValueReference()
                        },
                        new String[] {
                                entry.getValue()
                        });
            }

            else {
                throw new IllegalArgumentException("unsupported type: " + fmuVariable.getClass().getName());
            }
            if (!instance.getLastStatus().isOK()) {
                throw new RuntimeException(String.format(String.format("Setting input value on FMU failed (name: %s)", entry.getKey())));
            }
        }
    }

}
