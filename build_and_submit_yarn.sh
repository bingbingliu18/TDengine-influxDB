#!/bin/bash

# Build and submit script for TDengine to InfluxDB migration using REST connection
# This version uses YARN application mode

# Set variables
EMR_CLUSTER_ID="j-2HIE7CW7V2W7J"
EMR_MASTER_DNS="******.com"
REGION="us-east-1"
JAR_NAME="tdengine-influxdb-migration-1.0-SNAPSHOT.jar"
MAIN_CLASS="com.example.migration.TDengineToInfluxDBMigrationREST"
KEY_FILE="us-east-1.pem"

echo "Building the Flink application..."
mvn clean package

if [ $? -ne 0 ]; then
    echo "Build failed. Exiting."
    exit 1
fi

echo "Copying JAR to S3..."
aws s3 cp target/$JAR_NAME s3://influxdb-migration-artifacts/

if [ $? -ne 0 ]; then
    echo "Failed to copy JAR to S3. Exiting."
    exit 1
fi

echo "Copying JAR to EMR master node..."
scp -i $KEY_FILE -o StrictHostKeyChecking=no target/$JAR_NAME hadoop@$EMR_MASTER_DNS:~/$JAR_NAME

if [ $? -ne 0 ]; then
    echo "Failed to copy JAR to EMR master node. Exiting."
    exit 1
fi

echo "Submitting Flink job to EMR cluster $EMR_CLUSTER_ID using YARN application mode..."
ssh -i $KEY_FILE -o StrictHostKeyChecking=no hadoop@$EMR_MASTER_DNS << EOF
    # Set up environment
    export HADOOP_CLASSPATH=\$(hadoop classpath)
    
    # Submit the Flink job using YARN application mode
    flink run-application -t yarn-application \
        -c $MAIN_CLASS \
        -Dyarn.application.name="TDengine to InfluxDB Migration" \
        -Djobmanager.memory.process.size=1024m \
        -Dtaskmanager.memory.process.size=1024m \
        -Dtaskmanager.numberOfTaskSlots=2 \
        -Dparallelism.default=2 \
        ~/$JAR_NAME
EOF

if [ $? -ne 0 ]; then
    echo "Failed to submit job to EMR. Exiting."
    exit 1
fi

echo "Job submitted successfully. Check EMR console for status."
