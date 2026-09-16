// sanity check
NetAddr.localAddr

// configure to send
b = NetAddr.new("192.168.100.217", 8888);


// set LED
b.sendMsg("/Config/SetLED",1,0,1,255);

// set ID / OSC path
b.sendMsg("/Config/SetID","5");

// set diestination IP and Port
b.sendMsg("/Config/RequestStream",192,168,100,1,57120);

// check data is coming thru
OSCFunc.trace(true)
OSCFunc.trace(false)


// check we are sedning
b.sendMsg("/Config/GetConfig");
b.free
n = NetAddr.new("192.168.50.177", 57120);
o = OSCFunc({ arg msg, time, addr, recvPort; [msg, time, addr, recvPort].postln; }, '/4/Config');
o.free;




OSCFunc.trace(true)
OSCFunc.trace(false)

o = OSCFunc({ arg msg, time, addr, recvPort; [msg, time, addr, recvPort].postln; }, '/1/AnalogIn');
o.free;



o = OSCFunc({ arg msg, time, addr, recvPort; [msg, time, addr, recvPort].postln; }, '/1/AnalogIn');
o.free;
