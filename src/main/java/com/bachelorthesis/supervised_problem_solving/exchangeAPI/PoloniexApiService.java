package com.bachelorthesis.supervised_problem_solving.exchangeAPI;

import com.bachelorthesis.supervised_problem_solving.exchangeAPI.enums.Periods;
import com.bachelorthesis.supervised_problem_solving.exchangeAPI.pojo.chartData.ChartDataVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Thread.sleep;

public class PoloniexApiService {

    private final static Logger LOGGER = LoggerFactory.getLogger(PoloniexApiService.class);
    private final static String ROUTE = "https://poloniex.com/public?command=";
    private static final int TIMEOUT = 15 * 1000;
    private static final int MININUM_POLL_WINDOW_IN_MS = 250;
    private long lastApiCall;

    /**
     * getChartData(LocalDateTime.now().minusMonths(3), LocalDateTime.now(), currency, Periods.eighteenHundred.getNumVal());
     *
     * @param from
     * @param to
     * @param currency
     * @param period
     * @return
     */
    public List<ChartDataVO> getChartData(final LocalDateTime from, final LocalDateTime to, final String currency, final Periods period) throws IOException, InterruptedException {

        final String command = "returnChartData&currencyPair=";
        final String start = "&start=" + from.atZone(ZoneId.systemDefault()).toEpochSecond();
        final String end = "&end=" + to.atZone(ZoneId.systemDefault()).toEpochSecond();
        final String periodCommand = "&period=" + period.getPeriodValue();
        final String query = ROUTE + command + currency + start + end + periodCommand;

        List<ChartDataVO> openOrdersSingleCurrencyPOJO = new ObjectMapper().readValue(sendRequest(query), new TypeReference<>() {
        });

        setDateAndCurrency(openOrdersSingleCurrencyPOJO, currency);
        return openOrdersSingleCurrencyPOJO;
    }

    private void setDateAndCurrency(List<ChartDataVO> openOrdersSingleCurrencyPOJO, String currency) {
        openOrdersSingleCurrencyPOJO.forEach(entry -> {
            entry.setLocalDateTime(LocalDateTime
                    .ofEpochSecond(entry.getDate(), 0, ZoneOffset.UTC));
            entry.setCurrency(currency);
        });
    }

    public List<String> getAvailableCurrenciesAtExchange() throws IOException, InterruptedException {
        final String queryArgs = "https://poloniex.com/public?command=returnTicker";
        final String reply = sendRequest(queryArgs);
        final JsonNode jsonNode = new ObjectMapper().readTree(reply);

        final ArrayList<String> currencies = new ArrayList<>();

        jsonNode.fieldNames()
                .forEachRemaining(currencies::add);

        Collections.sort(currencies);
        return currencies;
    }

    private String sendRequest(final String queryArgs) throws IOException, InterruptedException {
        //prevent ip blocking from Poloniex
        if (System.currentTimeMillis() - lastApiCall < MININUM_POLL_WINDOW_IN_MS) {
            sleep(System.currentTimeMillis() - lastApiCall);
        }

        final URL obj = new URL(queryArgs);
        final HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        con.setConnectTimeout(TIMEOUT);
        con.setReadTimeout(TIMEOUT);
        // optional default is GET
        con.setRequestMethod("GET");

        //add request header
        con.setRequestProperty("User-Agent", "Mozilla/5.0");

        final BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        final String result = in.readLine();
        con.disconnect();
        lastApiCall = System.currentTimeMillis();

        return result;
    }
}