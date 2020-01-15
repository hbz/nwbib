curl --header "Accept-Encoding: gzip" "http://nwbibsnapshot.lobid.org/resources/search?q=di%C3%B6zese+AND+m%C3%BCnster&format=jsonl" > 700n-import.jsonl.gz
gunzip 700n-import.jsonl.gz
head 700n-import.jsonl > 700n-import-test.jsonl

