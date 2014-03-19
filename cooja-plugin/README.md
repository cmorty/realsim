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

* *addnode*;`nodeid`[;`node type`] Add a node with nodeid. The node type is optional
* *rmnode*;`nodeid` Remove the node with nodeid
* *setedge*;`srcID`;`dstID`;`PRR`;`RSSI`;`LQI` Set PRR, RSSI and LQI for the connction from srcID to dstID
* *rmedge*:`srcID`;`dstID` Remove the edge from srcID to dstID


### MoteSelection in RealSimFile
By default th node type chosen in the dropdown menu is used.

The special command `nodetype` has no timestamp, but applies to the rest of the file.

* `nodetype` without parameter resets the default node to dropdown of the RealSim-GUI
* `nodetype;<name of nodetype>` will choose that node type for all following `addnode` commands
* `nodetype;<name of nodetype>;<id>;<id2>;...` will set that node type for a specific node. 
	This overrides the default node configuration.

For example
```
nodetype;client
nodetype;server;1
```
will set all nodes to the nodetype client and node 1 to server.




