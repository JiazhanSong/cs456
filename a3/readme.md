Manfred Wu CS456 Assignment 3
m84wu/20614707

Compile code by typing "make". 
Run code by opening 6 windows: 1 on host1, 5 on host2. 
On host1, run: "./nse-linux386 <host2> <nse-port>"
On host2, run: "./router 1 <host1> <nse-port> <port1>"
On host2, run: "./router 2 <host1> <nse-port> <port2>"
On host2, run: "./router 3 <host1> <nse-port> <port3>"
On host2, run: "./router 4 <host1> <nse-port> <port4>"
On host2, run: "./router 5 <host1> <nse-port> <port5>"

Note: it is important to run in the exact order specified.

After execution, the programs will timeout after 2 seconds and log files:
  router1.log, router2.log, router3.log, router4.log, and router5.log
  will be created in the current directory.

This program was built and tested on the school's undergrad environment, 
  using hosts: "ubuntu1604-002" and "ubuntu1604-004".

The compiler version is: javac 9-internal.
The make version is: GNU Make 4.1.
