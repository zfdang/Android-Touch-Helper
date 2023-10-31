#!/bin/bash
# https://linuxopsys.com/topics/install-android-sdk-on-ubuntu
sudo apt install default-jdk -y

sudo apt install gradle -y

# this may took sometime . maybe 3 minutes. use vpn is better. such as proxychains4 
sudo add-apt-repository ppa:maarten-fonville/android-studio

# about 1.2GB install android-SDK and android studio(an IDE include  debugger , emulator...) 
sudo apt update  && sudo apt install android-studio -y 

# currently use  Android 11 and API level 30 .
# you can install from IDE.

