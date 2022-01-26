package com.raebo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class JhuProvider implements CovidDataProvider {
    // Preloaded data that can be used to serve requests immediately.
    private Map<String, Map<String, Integer>> STATE_TO_DATE_TO_NUM_CASES = new HashMap<>();
    private Map<String, Map<String, Integer>> STATE_TO_DATE_TO_NUM_TESTS = new HashMap<>();
    private Map<String, Map<String, Double>> STATE_TO_DATE_TO_CASES_PER_TEST = new HashMap<>();

    // Thead pool used for preloading data.
    private ExecutorService threadPool = Executors.newFixedThreadPool(20);
    // Requests wait on this until data has been preloaded.
    private Object PRELOADING_CONDITIONAL_VARIABLE = new Object();
    private boolean HAS_PRELOADING_COMPLETED = false;

    private static OffsetDateTime EARLIEST_AVAILABLE_DAILY_CSV_DATE = OffsetDateTime.of(LocalDate.of(2020, 4, 12),
            LocalTime.MIDNIGHT, ZoneOffset.UTC);
    private static String DAILY_CSV_URL_TEMPLATE = "https://raw.githubusercontent.com/CSSEGISandData/COVID-19/master/csse_covid_19_data/csse_covid_19_daily_reports_us/%s.csv";
    private static DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    public JhuProvider() {
        // Preload data asynchronously.
        threadPool.submit(() -> {
            try {
                preloadDataForAllStates();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    private <V> void putDateToValueForState(
            Map<String, Map<String, V>> stateToDateToValue, String date, V value, String state) {
        stateToDateToValue.compute(state, (key, dateToValue) -> {
            if (dateToValue == null) {
                dateToValue = new ConcurrentHashMap<>();
            }
            dateToValue.put(date, value);
            return dateToValue;
        });
    }

    private void preloadDataForAllStates() throws IOException {
        // These mappings are filled in locally in this method and then assigned to
        // the member variable mappings.
        Map<String, Map<String, Integer>> stateToDateToNumCases = new ConcurrentHashMap<>();
        Map<String, Map<String, Integer>> stateToDateToNumTests = new ConcurrentHashMap<>();
        Map<String, Map<String, Double>> stateToDateToCasesPerTest = new ConcurrentHashMap<>();

        List<Future<Void>> submittedTasks = new ArrayList<>();

        // Process daily CSVs from date to endDate.
        OffsetDateTime date = EARLIEST_AVAILABLE_DAILY_CSV_DATE;
        OffsetDateTime endDate = getLatestAvailableDailyCsvDate();
        while (!date.equals(endDate)) {
            final OffsetDateTime dateCopy = date;

            Future<Void> submittedTask = threadPool.submit(() -> {
                String dateStr = dateCopy.format(DATE_FORMATTER);

                URL csvUrl = new URL(String.format(DAILY_CSV_URL_TEMPLATE, dateStr));
                InputStream csvStream = csvUrl.openStream();
                Scanner csvScanner = new Scanner(csvStream);
                // Skip header.
                csvScanner.nextLine();
                while (csvScanner.hasNextLine()) {
                    String row = csvScanner.nextLine();
                    String[] fields = row.split(",");
                    if (fields.length < 12) {
                        continue;
                    }
                    String state = fields[0];
                    String numCasesStr = fields[5];
                    String numTestsStr = fields[11];
                    // Fields may not be available.
                    if (state.isEmpty()) {
                        continue;
                    }
                    if (!numCasesStr.isEmpty()) {
                        putDateToValueForState(stateToDateToNumCases, dateStr, Integer.parseInt(numCasesStr), state);
                    }
                    if (!numTestsStr.isEmpty()) {
                        putDateToValueForState(stateToDateToNumTests, dateStr, (int) Double.parseDouble(numTestsStr),
                                state);
                    }
                    if (!numCasesStr.isEmpty() && !numTestsStr.isEmpty()) {
                        putDateToValueForState(stateToDateToCasesPerTest, dateStr,
                                Integer.parseInt(numCasesStr) / Double.parseDouble(numTestsStr), state);
                    }
                }
                csvScanner.close();
                return null;
            });
            submittedTasks.add(submittedTask);

            date = date.plusDays(1);
        }

        for (Future<Void> task : submittedTasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException ex) {
                ex.printStackTrace();
                System.exit(-1);
            }
        }
        synchronized (STATE_TO_DATE_TO_NUM_CASES) {
            STATE_TO_DATE_TO_NUM_CASES = stateToDateToNumCases;
        }
        synchronized (STATE_TO_DATE_TO_NUM_TESTS) {
            STATE_TO_DATE_TO_NUM_TESTS = stateToDateToNumTests;
        }
        synchronized (STATE_TO_DATE_TO_CASES_PER_TEST) {
            STATE_TO_DATE_TO_CASES_PER_TEST = stateToDateToCasesPerTest;
        }
        synchronized (PRELOADING_CONDITIONAL_VARIABLE) {
            HAS_PRELOADING_COMPLETED = true;
            PRELOADING_CONDITIONAL_VARIABLE.notifyAll();
        }
    }

    /**
     * @return date of the latest available daily CSV
     * @throws IOException
     */
    private static OffsetDateTime getLatestAvailableDailyCsvDate() throws IOException {
        Instant currentUtcInstant = Instant.now();
        OffsetDateTime currentUtc = currentUtcInstant
                .atOffset(ZoneOffset.UTC)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        int dayOffset = 0;
        while (true) {
            if (dayOffset >= 3) {
                break;
            }
            OffsetDateTime previousDay = currentUtc.minusDays(dayOffset);
            String date = previousDay.format(DATE_FORMATTER);
            String csvUrl = String.format(DAILY_CSV_URL_TEMPLATE, date);
            try {
                InputStream stream = new URL(csvUrl).openStream();
                stream.close();
                return previousDay;
            } catch (FileNotFoundException ex) {
                dayOffset++;
            }
        }
        throw new RuntimeException("No CSVs available for the past three days");
    }

    /**
     * @return URL to the latest available daily CSV
     * @throws IOException
     */
    private static String getLatestAvailableDailyCsvUrl() throws IOException {
        return String.format(DAILY_CSV_URL_TEMPLATE, getLatestAvailableDailyCsvDate().format(DATE_FORMATTER));
    }

    @Override
    public Map<String, Double> getLatestCasesPerTest() throws IOException {
        Map<String, Double> stateToCasesPerTest = new HashMap<>();

        synchronized (STATE_TO_DATE_TO_CASES_PER_TEST) {
            if (!STATE_TO_DATE_TO_CASES_PER_TEST.isEmpty()) {
                // Data preloaded already.
                for (Map.Entry<String, Map<String, Double>> entry : STATE_TO_DATE_TO_CASES_PER_TEST.entrySet()) {
                    Map<String, Double> dateToCasesPerTest = entry.getValue();
                    String latestDate = Collections.max(dateToCasesPerTest.keySet(), (String date1, String date2) -> {
                        LocalDate d1 = LocalDate.parse(date1, DATE_FORMATTER);
                        LocalDate d2 = LocalDate.parse(date1, DATE_FORMATTER);
                        return d1.isAfter(d2) ? -1 : 1;
                    });
                    stateToCasesPerTest.put(entry.getKey(), dateToCasesPerTest.get(latestDate));
                }
                return stateToCasesPerTest;
            }
        }

        // Only one CSV needs to be downloaded, so if preloading has not completed,
        // just download the single CSV to skip waiting.

        // Open an InputStream to read the CSV.
        URL csvUrl = new URL(getLatestAvailableDailyCsvUrl());
        InputStream csvStream = csvUrl.openStream();
        Scanner csvScanner = new Scanner(csvStream);
        // Skip header.
        csvScanner.nextLine();
        // Read all the rows. Extract from each row the state name, number of cases,
        // and number of tests. Compute cases per test as number of cases / number
        // of tests. Map the state to cases per test.
        while (csvScanner.hasNextLine()) {
            String row = csvScanner.nextLine();
            String[] fields = row.split(",");
            if (fields.length < 12) {
                continue;
            }
            String state = fields[0];
            String numCasesStr = fields[5];
            String numTestsStr = fields[11];
            // Fields may not be available.
            if (state.isEmpty() || numCasesStr.isEmpty() || numTestsStr.isEmpty()) {
                continue;
            }
            int numCases = Integer.parseInt(numCasesStr);
            double numTests = Double.parseDouble(numTestsStr);
            double casesPerTest = numCases / numTests;
            stateToCasesPerTest.put(state, casesPerTest);
        }
        csvScanner.close();

        return stateToCasesPerTest;
    }

    @Override
    public Map<String, Double> getHistoricalCasesPerTest(String state) throws IOException {
        // Wait until preloading has completed.
        synchronized (PRELOADING_CONDITIONAL_VARIABLE) {
            while (!HAS_PRELOADING_COMPLETED) {
                try {
                    PRELOADING_CONDITIONAL_VARIABLE.wait();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                    System.exit(-1);
                }
            }
        }
        return STATE_TO_DATE_TO_CASES_PER_TEST.get(state);
    }

}
