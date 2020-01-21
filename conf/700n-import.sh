curl --header "Accept-Encoding: gzip" "http://lobid.org/resources/search?q=spatial.id%3A%22https%3A%2F%2Fnwbib.de%2Fspatial%23N96%22+OR+spatial.id%3A%22https%3A%2F%2Fnwbib.de%2Fspatial%23N97%22&format=jsonl" > 700n-import.jsonl.gz
gunzip 700n-import.jsonl.gz
head 700n-import.jsonl > 700n-import-test.jsonl

