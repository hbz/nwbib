# Build

[![](https://github.com/hbz/nwbib/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/hbz/nwbib/actions?query=workflow%3ABuild)

See the `.github/workflows/build.yml` file for details on the CI config used by Github Actions.

# Setup

Prerequisite: Java 8

`git clone https://github.com/hbz/nwbib.git ; cd nwbib`\
`wget http://downloads.typesafe.com/typesafe-activator/1.2.10/typesafe-activator-1.2.10-minimal.zip`\
`unzip typesafe-activator-1.2.10-minimal.zip`\
`./activator-1.2.10-minimal/activator test`

## Eclipse setup

Replace `test` with other Play commands, e.g. `"eclipse with-source=true"` (generate Eclipse project config files, then import as existing project in Eclipse), `~ run` (run in test mode, recompiles changed files on save, use this to keep your Eclipse project in sync while working, make sure to enable automatic workspace refresh in Eclipse: `Preferences` \> `General` \> `Workspace` \> `Refresh using native hooks or polling`).

## Production

Use `"start 8000"` to run in production background mode on port 8000 (hit Ctrl+D to exit logs). To restart a production instance running in the background, you can use the included `restart.sh` script (configured to use port 8000). For more information, see the [Play documentation](https://playframework.com/documentation/2.4.x/Home).

## Classification

This application uses the classifications in <https://github.com/hbz/lobid-vocabs/tree/master/nwbib>. The `nwbib-spatial` classification is based on Wikidata (see <http://slides.lobid.org/nwbib-wikidatacon/> for details). To update the local classification from Wikidata:

Delete the local Elasticsearch data:

`rm -rf ./data`

Delete the local Wikidata cache:

`rm conf/wikidata.json`

Re-generate the full classification:

`sbt "runMain SpatialToSkos"`

This creates a new `conf/nwbib-spatial.ttl` file with data from the new `conf/wikidata.json` and `conf/nwbib-spatial-conf.ttl`. The classification is consumed from <https://raw.githubusercontent.com/hbz/lobid-vocabs/master/nwbib/nwbib-spatial.ttl>, so we copy the result to the `lobid-vocabs` repo:

`cp conf/nwbib-spatial.ttl ../lobid-vocabs/nwbib/`

From here, we can diff, commit, and push our updated classification.

# License

GNU General Public License, version 2: <http://www.gnu.org/licenses/gpl-2.0.html>
