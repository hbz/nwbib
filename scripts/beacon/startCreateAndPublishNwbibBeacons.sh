#!/bin/bash
# Start script.
# Get a NWBib dump, create the beacons and copy them onto webserver.
# Activated via crontab.

# See https://github.com/hbz/nwbib/issues/335.

NWBIB_DUMP="nwbibDump.jsonl"

bash getNwbibDump.sh $NWBIB_DUMP
bash makeContributorsBeacon.sh $NWBIB_DUMP
bash makeSubjectsBeacon.sh $NWBIB_DUMP

scp *.bf lobid@emphytos:/usr/local/lobid/src/lobid.org/download/beacons/

