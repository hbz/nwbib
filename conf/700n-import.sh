curl --header "Accept-Encoding: gzip" "http://nwbibsnapshot.lobid.org/resources/search?q=Kreis+AND+Mettmann+AND+NOT+D%C3%BCsseldorf&format=jsonl" > 700n-import.jsonl.gz
gunzip 700n-import.jsonl.gz
head 700n-import.jsonl > 700n-import-test.jsonl

