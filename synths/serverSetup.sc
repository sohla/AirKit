(

// Use aggregate device for multi-device channels
// In:
// 		1	Operator microphone
// 		2	Child microphone
// Out:
//		1-2 Operator headphones
//		3-4 Child headphones

var input = ServerOptions.devices.indexOfEqual("USB Audio Device");
var output = ServerOptions.devices.indexOfEqual("External Headphones");

o = Server.local.options;
o.inDevice = ServerOptions.devices[input];
o.outDevice = ServerOptions.devices[output];
o.numOutputBusChannels = 4;
o.numInputBusChannels = 2;

s.reboot

)
