#!/bin/bash
# Create the beacons and copy them onto webserver at:
# http://lobid.org/download/beacons/

# see https://github.com/hbz/nwbib/issues/102

NWBIB_CONTRIBUTORS="nwbibContributors.bf"
TIMESTAMP="$(date +%Y-%m-%d)"
REVISIT=$(date +%Y-%m-%d --date="$TIMESTAMP  +1 month")
NWBIB_DUMP=$1

echo "#FORMAT: BEACON
#VERSION: 0.1
#PREFIX: https://d-nb.info/gnd/
#TARGET: https://nwbib.de/search?q=contribution.agent.id%3A"https%3A%2F%2Fd-nb.info%2Fgnd%2F{ID}"
#FEED: https://lobid.org/download/beacons/$NWBIB_CONTRIBUTORS
#CONTACT: lobid-Team im hbz <lobid-admin at hbz-nrw.de>
#NAME: Nordrhein-Westfälische Bibliographie (NWBib)
#INSTITUTION: Hochschulbibliothekszentrum des Landes Nordrhein-Westfalen (hbz)
#MESSAGE Literatur dieses Beitragenden in der Nordrhein-Westfälischen Bibliographie (NWBib)
#DESCRIPTION: This is an automatically generated BEACON file for all contributors of works catalogued in the Northrhine-Westphalian Bibliography.
#TIMESTAMP: $TIMESTAMP
#REVISIT: $REVISIT
#UPDATE: monthly
#LINK: https://www.w3.org/2000/01/rdf-schema#seeAlso
#EXAMPLES: 118515470
" > $NWBIB_CONTRIBUTORS

cat $NWBIB_DUMP | jq -r '. | select(.contribution != null) | .contribution[].agent.id'  | grep -v '_:b' | sort | uniq -c | sort -n -r | sed 's#\(.*\)https://d-nb.info/gnd/\(.*\)#\2|\1#g' | tr -d ' '|grep -v null >> $NWBIB_CONTRIBUTORS
