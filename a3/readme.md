Compile java code using makefile, use make command


Execution:
On hostX run the nse emulator with command: 

nse <hostY> <nse_port>

After running emulator, on hostY run the 5 routers:

java router <routerNum> <hostX> <nse_port> <router_port>


As per assignment specifications, script "router" runs my java program for all 5 routers
as background processes. Note, running the 5 routers separately works fine as well.

Usage: ./router $1 <hostX> <nse_port> <router_port1> <router_port2> <router_port3> <router_port4> <router_port5>

This program was tested in the school environment on hosts ubuntu1604-006 and ubuntu1604-008
Compiler and other tools used are the same as on the school environment.
