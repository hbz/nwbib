#!/bin/bash
# See http://redsymbol.net/articles/unofficial-bash-strict-mode/
set -uoe pipefail

# import nwbib-journals-new.csv, replacing current nwbib-journals.csv:
iconv -f cp1252 -t utf-8 -o nwbib-journals.csv nwbib-journals-new.csv
sed -ri 's/,""(http[^"]+?)""\s*"/","\1"/g' nwbib-journals.csv
sed -i 's/"""/"/g' nwbib-journals.csv
