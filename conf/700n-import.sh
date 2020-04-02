curl --header "Accept-Encoding: gzip" "http://nwbibsnapshot.lobid.org/resources/search?q=spatial.700n1b%3A%28Werne+AND+NOT+*Bochum*%29&format=jsonl" > 700n-import.jsonl.gz
gunzip 700n-import.jsonl.gz
head 700n-import.jsonl > 700n-import-test.jsonl
