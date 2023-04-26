package com.example.performance;

import com.example.performance.constant.Model;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class UserManagementSimulation extends Simulation {

    private final Config config;

    public UserManagementSimulation() {
        config = ConfigFactory.load("performance-test.conf");
        setUp(buildScenario().injectOpen(injectionProfile())
                .protocols(httpProtocolFromPath()));
    }

    private static HttpProtocolBuilder httpProtocolFromPath() {
        return http
                .baseUrl("http://localhost:8080")
                .disableWarmUp()
                .acceptHeader("application/json")
                .acceptCharsetHeader("UTF-8")
                .doNotTrackHeader("1")
                .acceptLanguageHeader("en-US,en;q=0.5")
                .acceptEncodingHeader("gzip, deflate")
                .userAgentHeader("Gatling/Performance Test");
    }

    private static Iterator<Map<String, Object>> userIdFeeder() {
        Iterator<Map<String, Object>> iterator;
        iterator = Stream.generate(() -> {
                    Map<String, Object> stringObjectMap = new HashMap<>();
                    stringObjectMap.put("firstName", "Kamilah");
                    stringObjectMap.put("lastName", "Gorczany");
                    stringObjectMap.put("address", "54653 Antonetta Spur, East Xavier, CT 91117");
                    stringObjectMap.put("contactNumber", "2081556237");
                    return stringObjectMap;
                })
                .iterator();
        return iterator;
    }

    private static ScenarioBuilder buildScenario() {
        return scenario("Load Test for user APIs")
                .feed(userIdFeeder())
                .exec(
                        http("Save user")
                                .post("/api/user")
                                .header("Content-Type", "application/json")
                                .body(StringBody("{\"firstName\": \"${firstName}\", \"lastName\": \"${lastName}\", \"address\": \"${address}\", \"contactNumber\": \"${contactNumber}\"}"))
                                .check(status().is(201), jsonPath("$.id").saveAs("id"))
                )
                .exec(
                        http("get user with id").get("/api/user/${id}")
                                .header("Content-Type", "application/json")
                                .check(status().is(200)));
    }

    private OpenInjectionStep injectionProfile() {
        Model model = Model.valueOf(config.getString("model"));
        OpenInjectionStep injectionStep;
        switch (model) {
            case RAMP_TO_CONSTANT: {
                injectionStep = rampToConstantModel();
                break;
            }
            case INJECT_ONCE: {
                injectionStep = injectOnceModel();
                break;
            }
            default:
                throw new RuntimeException("Invalid injection model");
        }
        return injectionStep;
    }

    private OpenInjectionStep.RampRate.RampRateOpenInjectionStep rampToConstantModel() {
        int totalDesiredUserCount = config.getInt("desiredUserCount");
        double userRampUpPerInterval = config.getInt("userRampUpPerInterval");
        double rampUpIntervalSeconds = config.getInt("rampUpIntervalSeconds");
        int totalRampUptimeSeconds = config.getInt("totalRampUptimeSeconds");
        int steadyStateDurationSeconds = config.getInt("steadyStateDurationSeconds");

        return rampUsersPerSec(userRampUpPerInterval / (rampUpIntervalSeconds / 60)).to(totalDesiredUserCount)
                .during(Duration.ofSeconds(totalRampUptimeSeconds + steadyStateDurationSeconds));
    }

    private OpenInjectionStep injectOnceModel() {
        int totalDesiredUserCount = config.getInt("desiredUserCount");
        return atOnceUsers(totalDesiredUserCount);
    }
}
