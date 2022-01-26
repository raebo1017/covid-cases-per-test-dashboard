package com.raebo;

import java.io.IOException;
import java.util.Map;

public interface CovidDataProvider {

    /**
     * Returns the latest cases per test for all US states.
     * 
     * @return mapping from US states to their latest cases per test
     * @throws IOException
     */
    Map<String, Double> getLatestCasesPerTest() throws IOException;

    /**
     * Returns the historical cases per test for a US state.
     * 
     * @param state
     * @return mapping from dates to cases per test
     * @throws IOException
     */
    Map<String, Double> getHistoricalCasesPerTest(String state) throws IOException;
}
