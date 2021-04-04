#!/bin/bash

set -e

./mvnw install -Dinvoker.streamLogs=true --batch-mode --show-version
