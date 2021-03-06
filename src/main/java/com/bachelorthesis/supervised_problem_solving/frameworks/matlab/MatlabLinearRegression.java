package com.bachelorthesis.supervised_problem_solving.frameworks.matlab;

import com.bachelorthesis.supervised_problem_solving.configuration.RuntimeDataStorage;
import com.bachelorthesis.supervised_problem_solving.enums.Indicators;
import com.bachelorthesis.supervised_problem_solving.services.algos.Algorithms;
import com.bachelorthesis.supervised_problem_solving.services.algos.MatrixService;
import com.bachelorthesis.supervised_problem_solving.services.exchangeAPI.poloniex.PoloniexApiService;
import com.bachelorthesis.supervised_problem_solving.services.exchangeAPI.poloniex.enums.Periods;
import com.bachelorthesis.supervised_problem_solving.services.exchangeAPI.poloniex.vo.ChartDataVO;
import matlabcontrol.*;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import scala.collection.mutable.StringBuilder;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static com.bachelorthesis.supervised_problem_solving.services.algos.FactorNames.getFactorNames;
import static org.apache.spark.sql.api.r.SQLUtils.createStructField;

@Service
public class MatlabLinearRegression {

    private static final int TRADING_FREQUENCY = 60;
    // delta between bars
    private static final int[] BAR_DELTA = new int[]{5, 10, 15, 20, 25, 30, 60};
    private final static Logger LOGGER = LoggerFactory.getLogger(MatlabLinearRegression.class);
    private final static String DEMO_CURRENCY = "BTC_XMR";
    private final RuntimeDataStorage runtimeDatastorage = new RuntimeDataStorage();
    @Autowired
    private PoloniexApiService poloniexApiService;
    private SparkSession sparkSession;
    private List<String> factorNames;

    public void startMatlabRegressionExperiment() {
        this.sparkSession = setupSpark();
        final LocalDateTime now = LocalDateTime.now();
        try {
            final List<ChartDataVO> pastData = poloniexApiService.
                    getChartData(now.minusMonths(18), now.minusMonths(10), DEMO_CURRENCY, Periods.fourHours);
            final List<ChartDataVO> testData = poloniexApiService.
                    getChartData(now.minusMonths(10), now.minusMonths(2), DEMO_CURRENCY, Periods.fourHours);

            setupVectorMatrices(pastData, testData, List.of(Indicators.RSI, Indicators.MACD));
            runMatlab();
        } catch (Exception exception) {
            LOGGER.error("Error fetching and execute trade signals for {}, with exception {}", this, exception);
        }
    }

    private void setupVectorMatrices(final List<ChartDataVO> pastData, List<ChartDataVO> testData, final List<Indicators> technicalIndicatorsList) throws IOException, MatlabInvocationException, MatlabConnectionException {
        factorNames = getFactorNames(technicalIndicatorsList, BAR_DELTA);

        createPastData(pastData, technicalIndicatorsList);
        createTestData(testData, technicalIndicatorsList);
    }

    private void createTestData(List<ChartDataVO> testData, List<Indicators> technicalIndicatorsList) throws IOException {
        setupEnvironment(testData, technicalIndicatorsList);
        final List<Double> futureReturns = Algorithms.getReturns(testData, TRADING_FREQUENCY);

        final Dataset<Row> indicatorDataSet = getIndicatorDataSet(testData, technicalIndicatorsList, factorNames);
        saveDataToCSV(indicatorDataSet, "testData", true);
        //saveDataToCSV(indicatorDataSet, "fullTable", true); not yet necessary

        final Dataset<Double> futureReturnsDataSet = sparkSession.createDataset(futureReturns, Encoders.DOUBLE());
        saveDataToCSV(futureReturnsDataSet, "returns", true);
    }

    private void createPastData(List<ChartDataVO> pastData, List<Indicators> technicalIndicatorsList) throws IOException {
        setupEnvironment(pastData, technicalIndicatorsList);
        final List<Double> futureReturns = Algorithms.getReturns(pastData, TRADING_FREQUENCY);

        final Dataset<Row> indicatorDataSet = getIndicatorDataSet(pastData, technicalIndicatorsList, factorNames);

        saveDataToCSV(indicatorDataSet, "trainingData", true);
        //saveDataToCSV(indicatorDataSet, "fullTable", true); not yet necessary

        final Dataset<Double> futureReturnsDataSet = sparkSession.createDataset(futureReturns, Encoders.DOUBLE());
        saveDataToCSV(futureReturnsDataSet, "futureReturns", true);
    }


    private void runMatlab() throws MatlabConnectionException, MatlabInvocationException {

        // create proxy
        final MatlabProxyFactoryOptions.Builder builder = new MatlabProxyFactoryOptions.Builder();

        final MatlabProxyFactory factory = new MatlabProxyFactory(builder.build());
        // get the proxy
        final MatlabProxy proxy = factory.getProxy();

        // call user-defined function (must be on the path)
        proxy.eval("addpath('" + getPath().toString() + "')");
        proxy.feval("BA_Michael_Bielang_Regression");

        // close connection
        proxy.disconnect();
    }

    private StringBuilder getPath() {
        final StringBuilder path = new StringBuilder(System.getProperty("user.dir").replace("\\", "\\\\"));
        path.append("\\\\matlab");
        return path;
    }

    private Dataset<Row> getIndicatorDataSet(List<ChartDataVO> chartDataVOList, List<Indicators> technicalIndicatorsList, List<String> factorNames) {
        final List<Row> rows = MatrixService.getRowList(chartDataVOList, factorNames, BAR_DELTA, technicalIndicatorsList);
        final StructField[] structFields = new StructField[factorNames.size()];

        for (int i = 0; i < factorNames.size(); i++) {
            structFields[i] = createStructField(factorNames.get(i), "double", true);
        }

        final StructType schemata = DataTypes.createStructType(structFields);

        return sparkSession.createDataFrame(rows, schemata);
    }

    private SparkSession setupSpark() {
        final SparkConf sparkConf = new SparkConf();
        sparkConf.setMaster("local[2]");
        sparkConf.setAppName("MLA");

        return SparkSession.builder().config(sparkConf).getOrCreate();
    }

    private void setupEnvironment(List<ChartDataVO> chartDataVOList, List<Indicators> technicalIndicatorsList) {
        runtimeDatastorage.findAndSetMaximumMatrixRows(chartDataVOList, technicalIndicatorsList, BAR_DELTA, TRADING_FREQUENCY);
        validateNumberOfDataPoints(chartDataVOList);
    }

    private void saveDataToCSV(Dataset<?> dataSet, String dataType, boolean withHeader) throws IOException {
        dataSet.coalesce(1).
                write().
                mode("overwrite").
                format("com.databricks.spark.csv").
                option("header", String.valueOf(withHeader)).
                save("tmp.csv");

        final FileSystem fileSystem = FileSystem.get(sparkSession.sparkContext().hadoopConfiguration());
        final File dir = new File(System.getProperty("user.dir") + "/tmp.csv/");
        final File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));

        fileSystem.rename(new Path(files[0].toURI()), new Path(System.getProperty("user.dir") + "/matlab/" + dataType + ".csv"));
        fileSystem.delete(new Path(System.getProperty("user.dir") + "/tmp.csv/"), true);
    }

    private void validateNumberOfDataPoints(List<ChartDataVO> chartDataVOList) {
        if (chartDataVOList.size() < RuntimeDataStorage.getMatrixRowLength()) {
            throw new IllegalArgumentException("Not enough data. Please deliver more");
        }
    }
}
