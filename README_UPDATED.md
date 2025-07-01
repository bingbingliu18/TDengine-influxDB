# TDengine to AWS InfluxDB Migration Project

This project implements a data migration solution from TDengine to AWS-managed InfluxDB using Apache Flink on Amazon EMR.

## Project Overview

The migration process extracts time series data from TDengine, transforms it to match the InfluxDB data model, and loads it into AWS InfluxDB. The implementation uses Apache Flink for scalable, distributed processing and runs on Amazon EMR.

## Components

### Source System: TDengine

- Version: 3.0.5.0
- Database: `test_db`
- Table: `sensors`
- Schema:
  - Columns: `ts` (TIMESTAMP), `temperature` (FLOAT), `humidity` (FLOAT), `pressure` (FLOAT), `status` (INT)
  - Tags: `location` (VARCHAR), `device_id` (VARCHAR)

### Target System: AWS InfluxDB

- URL: https://influxdb-3-5bc2krpa3h3ehv.us-east-1.timestream-influxdb.amazonaws.com:8086
- Organization: bingbing
- Bucket: test
- Measurement: sensors

### Processing Framework

- Apache Flink 1.16.0 on Amazon EMR
- EMR Cluster ID: j-2HIE7CW7V2W7J
- Region: us-east-1

## Implementation Details

### Connection Strategy

The migration uses TDengine's REST connection mode instead of the native connection mode to avoid native library dependencies in the distributed environment:

```java
// REST connection URL format
jdbc:TAOS-RS://host:port/database
```

### Data Transformation

The data transformation maps TDengine's schema to InfluxDB's data model:
- Numeric columns (temperature, humidity, pressure, status) become fields
- Tag columns (location, device_id) become tags
- Timestamp is preserved

### Key Files

- `src/main/java/com/example/migration/TDengineToInfluxDBMigrationREST.java`: Main Flink application
- `build_and_submit_rest.sh`: Script to build and submit the Flink job to EMR
- `check_tdengine.sh`: Script to verify TDengine is running and has data
- `verify_migration.py`: Python script to verify data was migrated to InfluxDB
- `MIGRATION_IMPLEMENTATION.md`: Detailed implementation documentation

## How to Run the Migration

1. Ensure TDengine is running and has data:
   ```bash
   ./check_tdengine.sh
   ```

2. Build and submit the Flink job to EMR:
   ```bash
   ./build_and_submit_rest.sh
   ```

3. Verify the migration was successful:
   ```bash
   python3 verify_migration.py
   ```

## Results

The migration successfully transferred all 10,014 records from TDengine to AWS InfluxDB, maintaining data integrity including timestamps, measurements, and tags. The migration process completed in approximately 21 seconds.

## Monitoring and Troubleshooting

- EMR cluster logs can be accessed through the AWS Management Console
- Flink job logs are available in the Flink web UI
- Local logs are generated during the migration process

## Next Steps

1. Implement incremental migration for new data
2. Add data validation to ensure data integrity
3. Optimize performance for larger datasets
4. Implement error handling and retry mechanisms

## References

- [TDengine JDBC Documentation](https://www.cnblogs.com/taosdata/p/16318975.html)
- [InfluxDB Client Documentation](https://github.com/influxdata/influxdb-client-java)
- [Apache Flink Documentation](https://nightlies.apache.org/flink/flink-docs-release-1.16/)
- [Amazon EMR Documentation](https://docs.aws.amazon.com/emr/)
