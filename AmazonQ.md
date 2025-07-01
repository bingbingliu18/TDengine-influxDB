# TDengine to AWS InfluxDB Migration Implementation

This document outlines the implementation of a data migration solution from TDengine to AWS-managed InfluxDB using Apache Flink on Amazon EMR.

## Project Structure

The migration project consists of the following components:

1. **Flink Application**: A Java application that reads data from TDengine and writes it to InfluxDB
2. **Build and Deployment Scripts**: Scripts to build the application and submit it to the EMR cluster
3. **Verification Tools**: Scripts to verify the migration was successful

## Key Files

- `pom.xml`: Maven project configuration with dependencies for Flink, TDengine REST API client, and InfluxDB client
- `src/main/java/com/example/migration/TDengineToInfluxDBMigrationREST.java`: Main Flink application using REST connection
- `build_and_submit.sh`: Script to build and submit the Flink job to EMR
- `check_tdengine.sh`: Script to verify TDengine is running and has data
- `verify_migration.py`: Python script to verify data was migrated to InfluxDB
- `run_migration.sh`: Main script to orchestrate the entire migration process
- `MIGRATION_README.md`: Documentation for the migration process

## Implementation Details

### Data Source (TDengine)

The Flink application connects to the local TDengine instance using REST API (port 6041) and reads data from the `sensors` table in the `test_db` database. The data includes:

- Timestamp
- Temperature, humidity, pressure measurements
- Status code
- Location and device ID tags

### Data Transformation

The data is read from TDengine and transformed into the InfluxDB data model:
- Numeric columns become fields
- Tag columns become tags
- Timestamp is preserved

### Data Sink (InfluxDB)

The transformed data is written to AWS-managed InfluxDB using the InfluxDB client library with the following configuration:
- URL: https://influxdb-3-5bc2krpa3h3ehv.us-east-1.timestream-influxdb.amazonaws.com:8086
- Organization: bingbing
- Bucket: test
- Measurement name: sensors

### Execution on EMR

The Flink job is submitted to the EMR cluster (j-2HIE7CW7V2W7J) in us-east-1 region, which has Flink 1.16.0 installed.

## How to Run the Migration

1. Ensure TDengine is running and has data:
   ```bash
   ./check_tdengine.sh
   ```

2. Run the complete migration process:
   ```bash
   ./run_migration.sh
   ```

3. Verify the migration was successful:
   ```bash
   ./verify_migration.py
   ```

## Monitoring and Troubleshooting

- EMR cluster logs can be accessed through the AWS Management Console
- Flink job logs are available in the Flink web UI
- Local logs are generated during the migration process

## Next Steps

1. Implement incremental migration for new data
2. Add data validation to ensure data integrity
3. Optimize performance for larger datasets
4. Implement error handling and retry mechanisms
