#!/bin/bash

set -e

./mvnw install -Dinvoker.streamLogs=true --settings .travis/settings.xml --batch-mode --show-version
