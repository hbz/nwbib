curl --header "Accept-Encoding: gzip" "http://lobid.org/resources/search?q=spatial.id%3A*&format=jsonl" > 700n-import.jsonl.gz
gunzip 700n-import.jsonl.gz
head -n 1000 700n-import.jsonl > 700n-import-test.jsonl

