curl --header "Accept-Encoding: gzip" "https://lobid.org/resources/search?q=inCollection.id%3A%22http%3A%2F%2Flobid.org%2Fresources%2FHT014846970%23%21%22+AND+inCollection.id%3A%22http%3A%2F%2Flobid.org%2Fresources%2FHT014176012%23%21%22+AND+%28+_exists_%3Aspatial+OR+subject.source%3A%22https%3A%2F%2Fnwbib.de%2Fsubjects%22+%29&format=jsonl" > 700n-import.jsonl.gz
gunzip 700n-import.jsonl.gz
head 700n-import.jsonl > 700n-import-test.jsonl
