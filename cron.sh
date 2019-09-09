#!/bin/bash
# See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# explicitly without "-e" for it should not exit immediately when failed but write a mail
set -uo pipefail

# Execute via crontab by hduser@weywot1:
# 00 1 * * * ssh sol@quaoar1 "cd /home/sol/git/nwbib ; bash -x cron.sh >> logs/cron.sh.log 2>&1"

IFS=$'\n\t'
RECIPIENT=lobid-admin

rm -rf ./data
rm conf/wikidata.json
sbt "runMain SpatialToSkos"

CODE=$?

if ! [ $CODE -eq 0 ]; then

echo "Non-0 code: $CODE"

MISSING_NWBIB="$(cat conf/qid-p6814-missing-in-nwbib.csv)"
MISSING_WIKID="$(cat conf/qid-p6814-missing-in-wiki.csv)"
LOG="$(tail -n 50 logs/application.log)"

MESSAGE="Missing in NWBib:\n$MISSING_NWBIB \n\nMissing in Wikidata:\n$MISSING_WIKID\n\nLog:\n$LOG"

echo -e "$MESSAGE" | mail -s "Alert nwbib-spatial" "$RECIPIENT@hbz-nrw.de"

fi
