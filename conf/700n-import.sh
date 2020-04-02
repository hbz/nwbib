curl --header "Accept-Encoding: gzip" "http://lobid.org/resources/search?q=spatial.id%3A*+AND+NOT+inCollection.id%3A%22http%3A%2F%2Flobid.org%2Fresources%2FHT014846970%23%21%22&format=jsonl" > 700n-import.jsonl.gz
gunzip 700n-import.jsonl.gz
head -n 1000 700n-import.jsonl > 700n-import-test.jsonl
cat 700n-import.jsonl | grep HT016542897 >> 700n-import-test.jsonl
