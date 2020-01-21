curl --header "Accept-Encoding: gzip" "http://nwbibsnapshot.lobid.org/resources/search?q=Bockum+AND+H%C3%B6vel&format=jsonl" > 700n-import.jsonl.gz
gunzip 700n-import.jsonl.gz
head 700n-import.jsonl > 700n-import-test.jsonl

