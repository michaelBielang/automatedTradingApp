package com.bachelorthesis.supervised_problem_solving.services.algos;

import com.bachelorthesis.supervised_problem_solving.configuration.RuntimeDataStorage;
import com.bachelorthesis.supervised_problem_solving.enums.Indicators;
import com.bachelorthesis.supervised_problem_solving.services.exchangeAPI.poloniex.vo.ChartDataVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

import static com.bachelorthesis.supervised_problem_solving.enums.Indicators.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MatrixServiceTest {

    private final static List<ChartDataVO> CHART_DATA_VOS_100 = new LinkedList<>();
    private final static int ELEMENTS_TO_BE_CREATED = 210;
    private final static int[] BAR_DELTA = new int[]{5, 10, 15};

    @BeforeAll
    public static void setup() {
        RuntimeDataStorage.setMatrixRowLength(ELEMENTS_TO_BE_CREATED - 15);
        for (int start = 0; start < ELEMENTS_TO_BE_CREATED; start++) {

            final ChartDataVO chartDataVO100 = new ChartDataVO();

            chartDataVO100.setClose(start);

            setDate(start, chartDataVO100);

            CHART_DATA_VOS_100.add(chartDataVO100);
        }
    }

    private static void setDate(final int start, final ChartDataVO chartDataVO100) {
        final LocalDateTime localDateTime = LocalDateTime.now().minusDays(ELEMENTS_TO_BE_CREATED - start);
        chartDataVO100.setLocalDateTime(localDateTime);
    }

    @Test
    void getFilledMatrix() {
        final List<Indicators> indicatorsList = List.of(RSI, MACD);
        final List<String> factorNames = FactorNames.getFactorNames(indicatorsList, BAR_DELTA);
        final List<Double> profit5 = Algorithms.getReturns(CHART_DATA_VOS_100, 5);
        final List<Double> profit10 = Algorithms.getReturns(CHART_DATA_VOS_100, 10);
        final List<Double> profit15 = Algorithms.getReturns(CHART_DATA_VOS_100, 15);
        final List<Double> rsi7 = Algorithms.getRsi(CHART_DATA_VOS_100, 7);
        final List<Double> rsi14 = Algorithms.getRsi(CHART_DATA_VOS_100, 14);
        final List<Double> rsi21 = Algorithms.getRsi(CHART_DATA_VOS_100, 21);

        INDArray indArray = MatrixService.fillMatrixWithPredictors(CHART_DATA_VOS_100, factorNames, BAR_DELTA, indicatorsList);
        final List<Double> profit5ColumnFromMatrix = new LinkedList<>();
        for (double v : indArray.getColumn(0).toDoubleVector()) {
            profit5ColumnFromMatrix.add(v);
        }
        final List<Double> profit10ColumnFromMatrix = new LinkedList<>();
        for (double v : indArray.getColumn(1).toDoubleVector()) {
            profit10ColumnFromMatrix.add(v);
        }
        final List<Double> profit15ColumnFromMatrix = new LinkedList<>();
        for (double v : indArray.getColumn(2).toDoubleVector()) {
            profit15ColumnFromMatrix.add(v);
        }
        final List<Double> rsi7Matrix = new LinkedList<>();
        for (double v : indArray.getColumn(3).toDoubleVector()) {
            rsi7Matrix.add(v);
        }
        final List<Double> rsi14Matrix = new LinkedList<>();
        for (double v : indArray.getColumn(4).toDoubleVector()) {
            rsi14Matrix.add(v);
        }
        final List<Double> rsi21Matrix = new LinkedList<>();
        for (double v : indArray.getColumn(5).toDoubleVector()) {
            rsi21Matrix.add(v);
        }
        assertEquals(profit5, profit5ColumnFromMatrix);
        assertEquals(profit10, profit10ColumnFromMatrix);
        assertEquals(profit15, profit15ColumnFromMatrix);

        assertEquals(rsi7, rsi7Matrix);
        assertEquals(rsi14, rsi14Matrix);
        assertEquals(rsi21, rsi21Matrix);
    }
}
