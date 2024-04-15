#!/bin/bash
# see https://github.com/hbz/nwbib/issues/106

TIMESTAMP="$(date +%Y-%m-%d)"
REVISIT=$(date +%Y-%m-%d --date="$TIMESTAMP  +1 month")
NWBIB_DUMP=$1
NWBIB_SUBJECTS="nwbibSubjects.bf"

echo "#FORMAT: BEACON
#VERSION: 0.1
#PREFIX: https://d-nb.info/gnd/
#TARGET: https://nwbib.de/search?subject=https%3A%2F%2Fd-nb.info%2Fgnd%2F{ID}
#FEED: https://lobid.org/download/beacons/$NWBIB_SUBJECTS
#CONTACT: lobid-Team im hbz <lobid-admin at hbz-nrw.de>
#NAME: Nordrhein-Westfälische Bibliographie (NWBib)
#INSTITUTION: Hochschulbibliothekszentrum des Landes Nordrhein-Westfalen (hbz)
#MESSAGE Literatur über diese Ressource in der Nordrhein-Westfälischen Bibliographie (NWBib)
#DESCRIPTION: This is an automatically generated BEACON file for all contributors of works catalogued in the Northrhine-Westphalian Bibliography.
#TIMESTAMP: $TIMESTAMP
#REVISIT: $REVISIT
#UPDATE: monthly
#LINK: https://xmlns.com/foaf/0.1/isPrimaryTopicOf
#EXAMPLES: 118515470
" > $NWBIB_SUBJECTS

cat $NWBIB_DUMP | jq -r '.|.subject[].componentList' |grep -v null | jq .[].gndIdentifier | sort |grep -v null | uniq -c | sort -n -r | tr -d ' ' | sed 's#\([0-9].*\)"\(.*\)"#\2|\1#g' >> $NWBIB_SUBJECTS
