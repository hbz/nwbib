#!/bin/bash
# As the NWBib data is just a relatively small dump, we get it all.

curl -L "https://lobid.org/resources/search?q=inCollection.id%3A%22http%3A%2F%2Flobid.org%2Fresources%2FHT014176012%23%21%22&format=bulk" > $1

