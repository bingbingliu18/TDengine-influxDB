#!/bin/bash

# Main script to orchestrate the TDengine to InfluxDB migration process
# Using YARN application mode for Flink job submission

# Set variables
EMR_CLUSTER_ID="j-2HIE7CW7V2W7J"
EMR_MASTER_DNS="******.com"
REGION="us-east-1"
JAR_NAME="tdengine-influxdb-migration-1.0-SNAPSHOT.jar"
MAIN_CLASS="com.example.migration.TDengineToInfluxDBMigrationREST"
KEY_FILE="us-east-1.pem"
LOG_FILE="migration_$(date +%Y%m%d_%H%M%S).log"

# Function to log messages
log() {
    local message="$1"
    local timestamp=$(date +"%Y-%m-%d %H:%M:%S")
    echo "[$timestamp] $message" | tee -a $LOG_FILE
}

# Function to check if a command succeeded
check_status() {
    if [ $? -eq 0 ]; then
        log "SUCCESS: $1"
    else
        log "ERROR: $1"
        log "Migration process failed. Exiting."
        exit 1
    fi
}

log "Starting TDengine to InfluxDB migration process using YARN application mode"

# Step 1: Check if TDengine is running and has data
log "Checking TDengine status..."
./check_tdengine.sh
check_status "TDengine check"

# Step 2: Build the Flink application
log "Building the Flink application..."
mvn clean package
check_status "Maven build"

# Step 3: Copy JAR to S3 for backup
log "Copying JAR to S3..."
aws s3 cp target/$JAR_NAME s3://influxdb-migration-artifacts/
check_status "S3 copy"

# Step 4: Copy JAR to EMR master node
log "Copying JAR to EMR master node..."
scp -i $KEY_FILE -o StrictHostKeyChecking=no target/$JAR_NAME hadoop@$EMR_MASTER_DNS:~/$JAR_NAME
check_status "JAR copy to EMR"

# Step 5: Submit the Flink job using YARN application mode
log "Submitting Flink job to EMR cluster $EMR_CLUSTER_ID using YARN application mode..."
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
check_status "Flink job submission"

# Step 6: Wait for the YARN application to complete
log "Waiting for YARN application to complete..."
# Get the latest application ID
YARN_APP_ID=$(ssh -i $KEY_FILE -o StrictHostKeyChecking=no hadoop@$EMR_MASTER_DNS "yarn application -list | grep 'TDengine to InfluxDB Migration' | awk '{print \$1}' | tail -1")

if [ -z "$YARN_APP_ID" ]; then
    log "Failed to get YARN application ID. Check EMR logs for details."
    exit 1
fi

log "Found YARN application ID: $YARN_APP_ID"

# Poll the application status until it completes
while true; do
    APP_STATUS=$(ssh -i $KEY_FILE -o StrictHostKeyChecking=no hadoop@$EMR_MASTER_DNS "yarn application -status $YARN_APP_ID | grep 'State :' | awk '{print \$3}'")
    log "Current status: $APP_STATUS"
    
    if [ "$APP_STATUS" == "FINISHED" ] || [ "$APP_STATUS" == "SUCCEEDED" ]; then
        log "YARN application completed successfully"
        break
    elif [ "$APP_STATUS" == "FAILED" ] || [ "$APP_STATUS" == "KILLED" ]; then
        log "YARN application failed with status: $APP_STATUS"
        exit 1
    fi
    
    log "Waiting for YARN application to complete... (sleeping for 30 seconds)"
    sleep 30
done

# Step 7: Verify the migration was successful
log "Verifying migration results..."
source /home/ubuntu/influxdb/migrate/venv/bin/activate
python3 verify_migration.py
check_status "Migration verification"

log "Migration process completed successfully!"
log "See MIGRATION_RESULTS.md for detailed results"

exit 0
