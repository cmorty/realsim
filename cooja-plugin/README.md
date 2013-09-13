RealSim Plugin
==============
The RealSim plugin allows to replay connections using the DGRM


Installation
------------
Adjust the `cooja` property in the `build.xml`  
run `ant`

Start Cooja:  
`Settings` -> `Cooja extensions` -> Set the check box at this path. 

RealSim File
------------
A RealSim File has the following format:  
`Time in ms`;`Command`;`Parameter1`;`Parameter2`;`...`

* *addnode*;`nodeid` Add a node with nodeid
* *rmnode*;`nodeid` Remove the node with nodeid
* *setedge*;`srcID`;`dstID`;`PRR`;`RSSI`;`LQI` Set PRR, RSSI and LQI for the connction from srcID to dstID
* *rmedge*:`srcID`;`dstID` Remove the edge from srcID to dstID

When importing a RealSim file all nodes have the same node type. To change that the simulation must be saved in a file. It is then possible to adjust the node type.
