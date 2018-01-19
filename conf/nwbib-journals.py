import re
import csv

input_file = 'nwbib-journals-2018-01-09.csv' # NWBib mail from I.N.
output_file = 'nwbib-journals.csv'

# Fix some issues in the input file:

with open(input_file, 'r') as input:
    with open(output_file, 'w') as output:
        for line in input.readlines():
            fixed = re.sub(r'"" *"\n', '"\n', line)
            fixed = re.sub(r'(nwbib\.de/)( |%20)+', r'\1', fixed)
            fixed = fixed.replace(',""', '","')
            output.write(fixed)

# Process the output file as CSV:

with open(output_file, newline='') as csvfile:
     journal_reader = csv.reader(csvfile, delimiter=',', quotechar='"')
     for row in journal_reader:
         print(' | '.join(row))