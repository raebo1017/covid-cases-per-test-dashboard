package com.raebo;

import static spark.Spark.*;

import java.util.Map;

import com.google.gson.Gson;

public class App {
    private static CovidDataProvider provider = new JhuProvider();
    private static Gson gson = new Gson();

    private static int getHerokuAssignedPort() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        if (processBuilder.environment().get("PORT") != null) {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }
        return 4567;
    }

    public static void main(String[] args) {
        port(getHerokuAssignedPort());

        options("/*", (req, res) -> {
            String accessControlRequestHeaders = req.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                res.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = req.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                res.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        after((req, res) -> {
            res.header("Access-Control-Allow-Origin", System.getenv("CASES_PER_TEST_FRONTEND_DOMAIN"));
        });

        get("/latest-cases-per-test", (req, res) -> {
            Map<String, Double> casesPerTestAllStates = provider.getLatestCasesPerTest();
            res.type("application/json");
            return gson.toJson(casesPerTestAllStates);
        });

        get("/historical-cases-per-test", (req, res) -> {
            Map<String, Double> dateToCasesPerTest = provider.getHistoricalCasesPerTest(req.queryParams("state"));
            res.type("application/json");
            return gson.toJson(dateToCasesPerTest);
        });
    }
}
