#!/bin/bash
#
# Copyright 2017 O2 Czech Republic, a.s.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Run the ingest/retrieve server

export CASSANDRA_SEED=localhost:9042
export HDFS_AUTHORITY=127.0.1.1:9000
export KAFKA_BROKERS=localhost:9092

BIN_DIR=$(dirname $0)
LOG_LEVEL=INFO
JAR=target/proxima-example-ingest-server.jar
CLASS=cz.o2.proxima.server.IngestServer


java -cp ${JAR} -DLOG_LEVEL=${LOG_LEVEL} -Djava.library.path=${BIN_DIR}/hadoop-native/ ${CLASS}
