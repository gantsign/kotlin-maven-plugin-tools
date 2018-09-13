#!/bin/bash

set -e

./mvnw install --settings .travis/settings.xml --batch-mode --show-version
