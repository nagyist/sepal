#!/usr/bin/env bash

apt-get -y update && apt-get install -y software-properties-common

# Repository for Java
add-apt-repository -y ppa:webupd8team/java

apt-get -y update && apt-get install -qq -y \
    supervisor \
    gettext

# Installing Java
echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
apt-get install -y oracle-java8-installer
