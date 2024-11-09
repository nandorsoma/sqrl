#!/bin/bash

echo 'Compiling...this takes about 10 seconds'
java -jar "/opt/sqrl/sqrl-cli.jar" "$@"

if [ "$1" == "run" ] || [ "$1" == "test" ]; then
   # Determine the jar to run
    if [ "$1" == "run" ]; then
        JAR_NAME="sqrl-run.jar"
    else
        JAR_NAME="sqrl-test.jar"
    fi
    # Start Flink if FLINK_HOST is not set
    if [ -z "$FLINK_HOST" ]; then
        echo "Starting Flink..."
        ${FLINK_HOME}/bin/start-cluster.sh
        ${FLINK_HOME}/bin/taskmanager.sh start
        export FLINK_HOST=localhost
        export FLINK_PORT=8081
    fi

    # Start Redpanda if KAFKA_HOST is not set
    if [ -z "$KAFKA_HOST" ]; then
        echo "Starting Redpanda..."
        rpk redpanda start --schema-registry-addr 0.0.0.0:8086 --overprovisioned --smp 1 --memory 1G --reserve-memory 0M --node-id 0 --check=false &
        export KAFKA_HOST=localhost
        export KAFKA_PORT=9092
        export PROPERTIES_BOOTSTRAP_SERVERS=localhost:9092
    fi

    # Start Postgres if POSTGRES_HOST is not set
    if [ -z "$POSTGRES_HOST" ]; then
        echo "Starting Postgres..."
        service postgresql start
        export POSTGRES_HOST=localhost
        export POSTGRES_PORT=5432
        export JDBC_URL="jdbc:postgresql://localhost:5432/datasqrl"
        export PGHOST="localhost"
        export PGUSER="postgres"
        export JDBC_USERNAME="postgres"
        export JDBC_PASSWORD="postgres"
        export PGPORT=5432
        export PGPASSWORD="postgres"
        export PGDATABASE="datasqrl"
    fi

    # Wait for Flink to start
    echo "Waiting for Flink to start..."
    until curl -s "http://$FLINK_HOST:$FLINK_PORT" >/dev/null 2>&1; do
        echo "Waiting for Flink REST API to be ready at $FLINK_HOST:$FLINK_PORT..."
        sleep 2
    done

    # Wait for Redpanda (Kafka) to start
#    echo "Waiting for Redpanda (Kafka) to start..."
#    until redpanda ping >/dev/null 2>&1; do
#        echo "Waiting for Redpanda to be ready at $KAFKA_HOST:$KAFKA_PORT..."
#        sleep 2
#    done

    # Wait for Postgres to start
    echo "Waiting for Postgres to start..."
    until pg_isready -h $POSTGRES_HOST -p $POSTGRES_PORT >/dev/null 2>&1; do
        echo "Waiting for Postgres to be ready at $POSTGRES_HOST:$POSTGRES_PORT..."
        sleep 2
    done

    # Set the classpath
    export UDF_JAR_DIR="/build/build/deploy/flink/lib"
    export SYSTEM_JAR_DIR="/opt/system_libs/"

    echo "All services are up. Starting the main application..."
    echo "$@"

    exec java -jar "/opt/sqrl/$JAR_NAME" "$@"
else
    # Execute any other command provided
    exec "$@"
fi
