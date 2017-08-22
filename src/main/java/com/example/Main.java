/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
@SpringBootApplication
public class Main {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Autowired
    private DataSource dataSource;

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Main.class, args);
    }

    public static String getHTML(String urlToRead) throws Exception {
        StringBuilder result = new StringBuilder();
        URL url = new URL(urlToRead);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(100000);
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        return result.toString();
    }

    @RequestMapping("/")
    String index() {
        return "index";
    }

    @RequestMapping("/db")
    String db(Map<String, Object> model) {
        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp)");
            stmt.executeUpdate("INSERT INTO ticks VALUES (now())");
            ResultSet rs = stmt.executeQuery("SELECT tick FROM ticks");

            ArrayList<String> output = new ArrayList<String>();
            while (rs.next()) {
                output.add("Read from DB: " + rs.getTimestamp("tick"));
            }

            model.put("records", output);
            return "db";
        } catch (Exception e) {
            model.put("message", e.getMessage());
            return "error";
        }
    }

    @Bean
    public DataSource dataSource() throws SQLException {
        if (dbUrl == null || dbUrl.isEmpty()) {
            return new HikariDataSource();
        } else {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            return new HikariDataSource(config);
        }
    }

    //  @RequestMapping("/latestsensordata", method = POST)
//  @ResponseBody
    @PostMapping("/latestsensordata")
    @ResponseBody
    public String getFoosBySimplePath(@RequestParam("lat") Double latitude,
                                      @RequestParam("long") Double longitude) {

        System.err.println("request started");

        List<SensorValue> results = new ArrayList<SensorValue>();
        SensorValue filteredSensorValue = null;

        try {
            String allData = getHTML("http://api.luftdaten.info/static/v1/data.json");
            System.err.println("this is not reached");

            JSONArray json = new JSONArray(allData);

            for (int i = 0; i < json.length(); i++) {
                SensorValue value = new SensorValue();
                JSONObject object = (JSONObject) json.get(i);
                JSONObject locationObject = object.getJSONObject("location");
                value.setLat(locationObject.getDouble("latitude"));
                value.setLng(locationObject.getDouble("longitude"));

                JSONArray sensorValues = object.getJSONArray("sensordatavalues");

                boolean isPSensor = false;
                for (int s = 0; s < sensorValues.length(); s++) {
                    JSONObject sensorValueObject = (JSONObject) sensorValues.get(s);
                    String valueType = sensorValueObject.getString("value_type");

                    if (valueType.equals("P1")) {
                        value.setP1(sensorValueObject.getDouble("value"));
                        isPSensor = true;
                    }
                    if (valueType.equals("P2")) {
                        value.setP2(sensorValueObject.getDouble("value"));
                        isPSensor = true;
                    }
                }

                value.setTimestamp(object.getString("timestamp"));

                results.add(value);
            }

        } catch (Exception e) {
            System.err.println("exception " + e.toString());
            e.printStackTrace();
        }

        filteredSensorValue = filterByLocationAndTime(results, new Location("requestLocation", latitude, longitude));

        if (filteredSensorValue != null) {
            return "p1: " + filteredSensorValue.getP1() + " p2: " + filteredSensorValue.getP2() + " lat: " +
                    filteredSensorValue.getLat() + " long: " + filteredSensorValue.getLng();
        } else {
            return "sensorvalue not found";
        }
    }

    public SensorValue filterByLocationAndTime(List<SensorValue> originalList, Location myLocation) {
        List<SensorValue> filteredList = new ArrayList<SensorValue>();
        Location closestLocation = searchClosestLocation(originalList, myLocation);

        System.err.println("closest location" + closestLocation.getLatitude());
        System.err.println("TEST 3 + original list size" + originalList.size());

        for (SensorValue value : originalList) {
            if (value.getLocation().getLatitude()==closestLocation.getLatitude()
                    && value.getLocation().getLongitude()==closestLocation.getLongitude()) {
                System.err.println("original list filled");
                filteredList.add(value);
            }
        }

        return filterByTime(filteredList);

    }

    public Location searchClosestLocation(List<SensorValue> originalList, Location myLocation) {
        float closestDistance = 9999999999999F;
        Location closestLocation = null;
        for (SensorValue value : originalList) {
            if (value.getLocation().distanceTo(myLocation) < closestDistance) {
                closestLocation = new Location(value.getLocation());
            }

        }
        return closestLocation;

    }

    public SensorValue filterByTime(List<SensorValue> originalList) {
        System.err.println("originallist size: " + originalList.size());

        Date closestDate = originalList.get(0).getTimestamp();
        SensorValue filteredSensorValue = originalList.get(0);

        for (SensorValue value : originalList) {
            if (closestDate.before(value.getTimestamp())) {
                System.err.println("filtered by time");
                filteredSensorValue = value;
            } else {
                System.err.println("timefilter comparison: thistime:" + closestDate.toString() + "othertime "+ value.getTimestamp());
            }
        }

        return filteredSensorValue;
    }

}
