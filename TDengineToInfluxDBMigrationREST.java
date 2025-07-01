package com.example.migration;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApi;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class TDengineToInfluxDBMigrationREST {
    private static final Logger LOG = LoggerFactory.getLogger(TDengineToInfluxDBMigrationREST.class);

    public static void main(String[] args) throws Exception {
        // Set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // Set parallelism to 1 for this example

        // Create a DataStream from TDengine
        DataStream<SensorReading> sensorReadings = env.addSource(new TDengineSource());

        // Transform and write to InfluxDB
        sensorReadings
            .flatMap(new EnrichSensorData())
            .addSink(new InfluxDBSink());

        // Execute the Flink job
        env.execute("TDengine to InfluxDB Migration using REST");
    }

    /**
     * Source function to read data from TDengine using REST connection
     */
    public static class TDengineSource extends RichSourceFunction<SensorReading> {
        private static final long serialVersionUID = 1L;
        private transient Connection conn = null;
        private volatile boolean isRunning = true;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            // Load TDengine JDBC driver
            Class.forName("com.taosdata.jdbc.TSDBDriver");
            Class.forName("com.taosdata.jdbc.rs.RestfulDriver");
            
            LOG.info("Connecting to TDengine using REST connection...");
            
            // Connect to TDengine using REST connection
            // Format: jdbc:TAOS-RS://host:port/database
            String jdbcUrl = "jdbc:TAOS-RS://172.31.65.128:6041/test_db";
            conn = DriverManager.getConnection(jdbcUrl, "root", "taosdata");
            
            LOG.info("Connected to TDengine database using REST connection");
        }

        @Override
        public void run(SourceContext<SensorReading> ctx) throws Exception {
            // Query data from TDengine
            String sql = "SELECT ts, temperature, humidity, pressure, status, location, device_id FROM sensors";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                LOG.info("Executing query: {}", sql);
                
                while (rs.next() && isRunning) {
                    Timestamp ts = rs.getTimestamp("ts");
                    float temperature = rs.getFloat("temperature");
                    float humidity = rs.getFloat("humidity");
                    float pressure = rs.getFloat("pressure");
                    int status = rs.getInt("status");
                    String location = rs.getString("location");
                    String deviceId = rs.getString("device_id");
                    
                    SensorReading reading = new SensorReading(
                        ts.toInstant(),
                        temperature,
                        humidity,
                        pressure,
                        status,
                        location,
                        deviceId
                    );
                    
                    ctx.collect(reading);
                }
            }
            
            LOG.info("Finished reading data from TDengine");
        }

        @Override
        public void cancel() {
            isRunning = false;
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    LOG.error("Error closing TDengine connection", e);
                }
            }
        }

        @Override
        public void close() throws Exception {
            if (conn != null) {
                conn.close();
            }
            super.close();
        }
    }

    /**
     * Sink function to write data to InfluxDB
     */
    public static class InfluxDBSink extends RichSinkFunction<Tuple2<SensorReading, Map<String, String>>> {
        private static final long serialVersionUID = 1L;
        private transient InfluxDBClient influxDBClient;
        private transient WriteApi writeApi;

        // InfluxDB connection parameters
        private final String influxUrl = "https://influxdb-3-5bc2krp******m:8086";
        private final String token = "Q****ZisWotDcm8Jr40tf9vMTVEJj7Vl5pDkWtmUbkOXM-A==";
        private final String org = "bingbing";
        private final String bucket = "test";

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            
            // Create InfluxDB client
            influxDBClient = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), org, bucket);
            writeApi = influxDBClient.getWriteApi();
            
            LOG.info("Connected to InfluxDB at {}", influxUrl);
        }

        @Override
        public void invoke(Tuple2<SensorReading, Map<String, String>> value, Context context) {
            SensorReading reading = value.f0;
            Map<String, String> tags = value.f1;
            
            // Create a data point
            Point point = Point.measurement("sensors")
                .time(reading.getTimestamp(), WritePrecision.NS)
                .addField("temperature", reading.getTemperature())
                .addField("humidity", reading.getHumidity())
                .addField("pressure", reading.getPressure())
                .addField("status", reading.getStatus());
            
            // Add tags
            for (Map.Entry<String, String> tag : tags.entrySet()) {
                point.addTag(tag.getKey(), tag.getValue());
            }
            
            // Write to InfluxDB
            writeApi.writePoint(point);
            LOG.debug("Written point to InfluxDB: {}", point);
        }

        @Override
        public void close() throws Exception {
            if (writeApi != null) {
                writeApi.close();
            }
            if (influxDBClient != null) {
                influxDBClient.close();
            }
            super.close();
        }
    }

    /**
     * Function to enrich sensor data with additional metadata
     */
    public static class EnrichSensorData implements FlatMapFunction<SensorReading, Tuple2<SensorReading, Map<String, String>>> {
        private static final long serialVersionUID = 1L;

        @Override
        public void flatMap(SensorReading reading, Collector<Tuple2<SensorReading, Map<String, String>>> out) {
            Map<String, String> tags = new HashMap<>();
            tags.put("location", reading.getLocation());
            tags.put("device_id", reading.getDeviceId());
            
            out.collect(new Tuple2<>(reading, tags));
        }
    }

    /**
     * POJO class to represent a sensor reading
     */
    public static class SensorReading {
        private Instant timestamp;
        private float temperature;
        private float humidity;
        private float pressure;
        private int status;
        private String location;
        private String deviceId;

        public SensorReading() {
        }

        public SensorReading(Instant timestamp, float temperature, float humidity, float pressure, int status, String location, String deviceId) {
            this.timestamp = timestamp;
            this.temperature = temperature;
            this.humidity = humidity;
            this.pressure = pressure;
            this.status = status;
            this.location = location;
            this.deviceId = deviceId;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }

        public float getTemperature() {
            return temperature;
        }

        public void setTemperature(float temperature) {
            this.temperature = temperature;
        }

        public float getHumidity() {
            return humidity;
        }

        public void setHumidity(float humidity) {
            this.humidity = humidity;
        }

        public float getPressure() {
            return pressure;
        }

        public void setPressure(float pressure) {
            this.pressure = pressure;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        @Override
        public String toString() {
            return "SensorReading{" +
                    "timestamp=" + timestamp +
                    ", temperature=" + temperature +
                    ", humidity=" + humidity +
                    ", pressure=" + pressure +
                    ", status=" + status +
                    ", location='" + location + '\'' +
                    ", deviceId='" + deviceId + '\'' +
                    '}';
        }
    }
}
