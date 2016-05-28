Netplay Server
==============

This server implements all game management and forwarding functionality for the
netplay project.

Setup and Building
------------------

Clone the repository and initialize submodules.

    git clone https://github.com/water-works

**Important:** Don't forget to initialize submodules! We use git submodules for
proto definitions that are shared between the client and the server. Failing to
do this will lead to a broken build and will needlessly confuse and frustrate
you:

    git submodule update --init --recursive

Build the project using gradle. This step will also run all tests. Please report
all broken tests to XXX@XXX.pizza

    gradle build

Running the Server
------------------

Once you've built the server as described above, you'll find .tar and .zip archives for the server in build/distributions. Decompress them and start a server:

    unzip build/distributions/netplay-server.zip -d /tmp
    cd /tmp/netplay-server
    ./bin/netplay-server -p 54545

Eclipse Project Support
-----------------------

Gradle can also generate a nice Eclipse project and you can import as an existing project in Eclipse:

    gradle eclipse

Enjoy!
