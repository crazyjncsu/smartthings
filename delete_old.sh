#!/bin/ls
smartthings devices -j | jq ".[] | select(.label | startswith(\"Old\")) | .deviceId" -r |
while IFS= read -r deviceID
do
  echo "Deleting old device $deviceID"
  smartthings devices:delete $deviceID
done

