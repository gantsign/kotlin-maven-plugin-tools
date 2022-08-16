#!/bin/bash

set -e

./mvnw install \
    "-Drevision=${GIT_TAG:-development-SNAPSHOT}" \
    -Dinvoker.streamLogs=true \
    --batch-mode \
    --show-version
