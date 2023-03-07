#!/bin/ls
locationID=$(smartthings locations -j | jq '.[].locationId' -r)
echo "Location is $locationID" 
roomID=$(smartthings locations:rooms -l=$locationID -j | jq ".[] | select (.name == \"$1\") | .roomId" -r)
echo "Room is $roomID"
smartthings devices -j | jq ".[] | select(.label | startswith(\"$1\")) | .deviceId" -r |
  while IFS= read -r deviceID
  do
    echo "Updating room for device $deviceID"
    echo "{'roomId':'$roomID'}" | smartthings devices:update $deviceID
  done

