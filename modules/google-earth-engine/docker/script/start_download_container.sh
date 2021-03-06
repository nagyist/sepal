#!/usr/bin/env bash

account=$1
privateKeyPath=$2
downloadDir=$3
worker_user=$4

for i in {30..0}; do
    if [ $(getent passwd $worker_user | wc -l) -eq 1 ]; then
        break
    fi
    echo "Waiting for user $worker_user to be initialized..."
    /bin/sleep 1
done
if [ "$i" = 0 ]; then
    echo >&2 "User $worker_user not initialized"
    exit 1
else
    echo "User $worker_user initialized"
fi

exec su - $worker_user -c "python /src/download_server.py $account $privateKeyPath $downloadDir $worker_user"
