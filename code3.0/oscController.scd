// NetAddr.new("192.168.70.165", 3333).sendMsg(*msg); //connected to pi
// NetAddr.new("192.168.2.1", 3333).sendMsg(*msg); // Mac Mini connected to SensiLab (ethernet)
// NetAddr.new("192.168.70.232", 3333).sendMsg(*msg); // iPhone connected 
// NetAddr.new("192.168.70.48", 3333).sendMsg(*msg); // connected to Laptop


var machine = Require("machine.scd");

// var oscMessageTag  = "CombinedDataPacket";
var oscMessageTag  = "IMUFusedData"; // this is coming from AirSticks
var outAddr = NetAddr.new(machine[\outAddr][0], machine[\outAddr][1]);
var airstickListeners = [];
var numAirwareVirtualDevices = machine[\numDevices];
var oscThru = false;
//------------------------------------------------------------
// Models
//------------------------------------------------------------

var twoCh = (\x: 0, \y:0);
var threeCh = (\x: 0, \y:0, \z:0);
var fourCh = (\w: 0, \x: 0, \y:0, \z:0);

var paramsProto = (
    \volume: 0,
    \sensitivity: 0,
);

var listenersProto = (
    \airware:nil,
    \battery:nil,
    \inc:nil,
    \dec:nil,
    \digiIn:nil

);

var sensorsProto = (
    \gyroEvent: threeCh,
    \gyroMass: 0,
    \rrateEvent: threeCh,
    \rrateMass: 0,
    \accelEvent: threeCh,
    \accelMass: 1,
    \quatEvent: fourCh,
    \quatReference: fourCh,
    \quatCalibrated: fourCh,
    \velocity: threeCh,
    \lastTimestamp: 0,
    \isCalibrated: false,
    \ampValue: 0,
    \rotateEvent: threeCh,
    \digiInEvent: Array.fill(4,0),
);

var dataSizeOptions = [100,200,300,400];

var deviceProto = (
    \name: "default",
    \ip: "127.0.0.1",
    \index: 0,
    \personality: 0,
    \port: 57120,
    \color: Color.red,
    \volts: 0.0,
    \charge: 0.0,
    \enabled: true, // are we running
    \dataSize: dataSizeOptions[0],
    \listeners: Event.new(proto:listenersProto),
    \env: nil,	// Environment for injected code
    \procRout: nil,	// Routine calls ~next every ~fps
    \sensors: Event.new(proto:sensorsProto),
    \sensorBus: Bus.control(s,7),
    \incPersonality: {},
    \decPersonality: {},
    \params: Event.new(proto:paramsProto),

);

//------------------------------------------------------------
// Quaternion Functions  
//------------------------------------------------------------

//------------------------------------------------------------
// Create quaternion from w,x,y,z components
var quatNew = { |w, x, y, z|
    (w: w, x: x, y: y, z: z)
};

//------------------------------------------------------------
// Quaternion multiplication
var quatMul = { |q1, q2|
    var a = q1.w, b = q1.x, c = q1.y, d = q1.z;
    var a2 = q2.w, b2 = q2.x, c2 = q2.y, d2 = q2.z;
    
    quatNew.value(
        (a * a2) - (b * b2) - (c * c2) - (d * d2),  // w
        (a * b2) + (b * a2) + (c * d2) - (d * c2),  // x
        (a * c2) - (b * d2) + (c * a2) + (d * b2),  // y
        (a * d2) + (b * c2) - (c * b2) + (d * a2)   // z
    )
};

//------------------------------------------------------------
// Quaternion addition
var quatAdd = { |q1, q2|
    quatNew.(q1.w + q2.w, q1.x + q2.x, q1.y + q2.y, q1.z + q2.z)
};

//------------------------------------------------------------
// Quaternion subtraction
var quatSub = { |q1, q2|
    quatNew.(q1.w - q2.w, q1.x - q2.x, q1.y - q2.y, q1.z - q2.z)
};

//------------------------------------------------------------
// Scale quaternion by scalar
var quatScale = { |quat, scalar|
    quatNew.(quat.w * scalar, quat.x * scalar, quat.y * scalar, quat.z * scalar)
};

//------------------------------------------------------------
// Quaternion norm
var quatNorm = { |quat|
    sqrt(quat.w.squared + quat.x.squared + quat.y.squared + quat.z.squared)
};

//------------------------------------------------------------
//------------------------------------------------------------


//------------------------------------------------------------
// Simple 3D vector creation and operations
var vec3New = { |x, y, z|
    (x: x ? 0, y: y ? 0, z: z ? 0)
};

//------------------------------------------------------------
var vec3Add = { |v1, v2|
    vec3New.(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z)
};

//------------------------------------------------------------
var vec3Sub = { |v1, v2|
    vec3New.(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z)
};

//------------------------------------------------------------
var vec3Scale = { |vec, scalar|
    vec3New.(vec.x * scalar, vec.y * scalar, vec.z * scalar)
};

//------------------------------------------------------------
var vec3Magnitude = { |vec|
    sqrt(vec.x.squared + vec.y.squared + vec.z.squared)
};




//------------------------------------------------------------
// Angular difference function for proper wrap-around handling
var angleDiff = { |newAngle, oldAngle|
    var diff = newAngle - oldAngle;
    // Wrap difference to [-pi, pi] range
    while { diff > pi } { diff = diff - (2*pi) };
    while { diff < pi.neg } { diff = diff + (2*pi) };
    diff
};





//------------------------------------------------------------
// Convert quaternion to Euler angles (matches your Quaternion.sc asEuler)
// var quatToEuler = { |quat|
//     var q = [quat.w, quat.x, quat.y, quat.z];
//     var euler = Array.newClear(3);
    
//     euler[0] = atan2( (2 * q[1] * q[2]) - (2 * q[0] * q[3]), (2 * q[0]*q[0]) + (2 * q[1] * q[1]) - 1); // psi
//     euler[1] = asin( (2 * q[1] * q[3]) + (2 * q[0] * q[2])).neg; // theta
//     euler[2] = atan2( (2 * q[2] * q[3]) - (2 * q[0] * q[1]), (2 * q[0] * q[0]) + (2 * q[3] * q[3]) - 1); // phi
    
//     euler
// };


var quatToEuler = { |quat|
    var w = quat.w, x = quat.x, y = quat.y, z = quat.z;
    var euler = Array.newClear(3);
    var sinr_cosp, cosr_cosp, sinp, siny_cosp, cosy_cosp;
    
    // Roll (x-axis rotation)
    sinr_cosp = 2.0 * ((w * x) + (y * z));
    cosr_cosp = 1.0 - (2.0 * ((x * x) + (y * y)));
    euler[0] = atan2(sinr_cosp, cosr_cosp);
    
    // Pitch (y-axis rotation)
    sinp = 2.0 * ((w * y) - (z * x));
    euler[1] = if(sinp.abs >= 1,
        { (pi / 2) * sinp.sign },  // gimbal lock handling
        { asin(sinp) }
    );
    
    // Yaw (z-axis rotation)
    siny_cosp = 2.0 * ((w * z) + (x * y));
    cosy_cosp = 1.0 - (2.0 * ((y * y) + (z * z)));
    euler[2] = atan2(siny_cosp, cosy_cosp);
    
    euler
};
//------------------------------------------------------------
// Calibrate device orientation - stores current quaternion as reference
var calibrateDevice = { |devicePort|
    var device = ~devices.at(devicePort);
    if(device.notNil, {
        var currentQuat = device.sensors.quatEvent;
        device.sensors.quatReference = currentQuat.copy;
        device.sensors.isCalibrated = true;
        postf("Device % calibrated at orientation: w=% x=% y=% z=%\n", 
            devicePort, currentQuat.w, currentQuat.x, currentQuat.y, currentQuat.z);
    }, {
        postf("Device % not found for calibration\n", devicePort);
    });
};

//------------------------------------------------------------
//  Deveice Data listener
//------------------------------------------------------------
var addOSCDeviceListeners = {|d|

    var na = NetAddr.new(d.ip, d.port);
    var patternBase = "/%/" ++ oscMessageTag;
    var batteryBase = "/%/" ++ "Battery";
    var digiInBase = "/%/" ++ "DigiIn";

    // listen to all the airware that are connected (1 ip/port)
    numAirwareVirtualDevices.do({|i|


        var address = NetAddr.new(d.ip, d.port - i);
        var pattern = patternBase.format(i+1);
        var rx,ry,rz,ox=nil,oy=nil,oz=nil; //rotation integration
        var deltaTime = 0, deltaVelocity = 0;

        d.listeners.battery = OSCFunc({ |msg, time, addr, recvPort|
            d.volts = msg[1].asFloat;
            d.charge = msg[2].asFloat;
            // "battery [%]: %v %%".format(d.port,d.volts, d.charge).postln;
        }, batteryBase.format(i+1), address);

        d.listeners.digiIn = OSCFunc({ |msg, time, addr, recvPort|
          if(~devices.at(addr.port+i) != nil,{
            d.sensors.digiInEvent.put(msg[1].asInteger,msg[2].asInteger);
          });
        }, digiInBase.format(i+1), address);

        d.listeners.airware = OSCFunc({ |msg, time, addr, recvPort|
            var sx,sy,sz,qe,ss,r, rq, rr, rtr;
            var tr;

            if(~devices.at(addr.port+i) != nil,{
                var oq = ~devices.at(addr.port+i).sensors.quatEvent;
                var bus = ~devices.at(addr.port+i).sensorBus;

                bus.setn([
                    msg[1].asFloat * 0.1,
                    msg[2].asFloat * 0.1,
                    msg[3].asFloat * 0.1,
                    msg[7].asFloat, //w
                    msg[4].asFloat, //x
                    msg[5].asFloat, //y
                    msg[6].asFloat, //z
                ]);
                // bus.getn(7).postln;
                
                // Store Accel.
                ~devices.at(addr.port+i).sensors.accelEvent = (
                    \x:msg[1].asFloat * 0.1,
                    \y:msg[2].asFloat * 0.1,
                    \z:msg[3].asFloat * 0.1
                );

                // Store Quat.
                ~devices.at(addr.port+i).sensors.quatEvent = (
                    \x:msg[4].asFloat,
                    \y:msg[5].asFloat,
                    \z:msg[6].asFloat,
                    \w:msg[7].asFloat
                );

                // Calculate Gyro from Quat
                qe = ~devices.at(addr.port+i).sensors.quatEvent;

                // Apply calibration if device is calibrated
                if(~devices.at(addr.port+i).sensors.isCalibrated, {
                    var qRef = ~devices.at(addr.port+i).sensors.quatReference;
                    var qRefNeg = quatScale.value(quatNew.value(qRef.w, qRef.x, qRef.y, qRef.z), -1);
                    var qRefInv = quatNew.value(qRef.w, qRef.x.neg, qRef.y.neg, qRef.z.neg); // Simple conjugate
                    qe = quatMul.value(qRefInv, qe); // Relative rotation from reference
                    ~devices.at(addr.port+i).sensors.quatCalibrated = q;
                });

                // Convert
                r = quatToEuler.value(qe);
                tr = [r[0],r[1],r[2]];

                // Store Gyro. in radians
                ~devices.at(addr.port+i).sensors.gyroEvent = (
                    \x:tr[0].asFloat,
                    \y:tr[1].asFloat,
                    \z:tr[2].asFloat);

                // Get raw Euler angles for rate calculation
                rx = tr[0].asFloat;
                ry = tr[1].asFloat;
                rz = tr[2].asFloat;

                // Calculate angular rate using proper difference function
                if(ox.notNil and: oy.notNil and: oz.notNil, {
                    ~devices.at(addr.port+i).sensors.rrateEvent = (
                        \x:angleDiff.value(rx, ox),
                        \y:angleDiff.value(ry, oy),
                        \z:angleDiff.value(rz, oz));
                }, {
                    // Initialize on first measurement
                    ~devices.at(addr.port+i).sensors.rrateEvent = (
                        \x:0,
                        \y:0,
                        \z:0);
                });

                ox = rx;
                oy = ry;
                oz = rz;


                // Velocity
                deltaTime = time - ~devices.at(addr.port+i).sensors.lastTimestamp;
                deltaVelocity = vec3Scale.(~devices.at(addr.port+i).sensors.accelEvent, deltaTime);
                ~devices.at(addr.port+i).sensors.velocity = vec3Add.(~devices.at(addr.port+i).sensors.velocity, deltaVelocity);
                ~devices.at(addr.port+i).sensors.velocity = vec3Scale.(~devices.at(addr.port+i).sensors.velocity, 0.3);

                ~devices.at(addr.port+i).sensors.lastTimestamp = time;
            });
        }, pattern, address);
    });
};

//------------------------------------------------------------
// Device params : volume and sensitivity
//
// Stored on the device model, owned here, changed only through
// /airkit/param so every surface (panel, webapp, MIDI) sees the same
// value and hears about it on /airkit/paramIs.
//
// Persisted against the stick's own ID - d.index, set with
// /Config/SetID and carried in its OSC address. NOT the IP: AirStick
// leases are dynamic, and d.ip is written once and never refreshed. NOT
// the port either, which only encodes the ID for as long as every stick
// happens to share a source port.
//
// So params follow the slot, not the hardware : a spare stick set to ID
// 2 inherits slot 2's place in the mix.
//------------------------------------------------------------
var paramFile = machine[\stateDir] +/+ "deviceParams.scd";
var paramStore = ();
var paramsDirty = false;

var loadParams = {
    var str = if(File.exists(paramFile), { File.readAllString(paramFile) });
    var ev = str !? { str.interpret };      // nil on a malformed file, never throws
    if(ev.isKindOf(Event), { paramStore = ev }, {
        if(str.notNil, {
            "[params] % unreadable - starting from defaults".format(paramFile).warn;
        });
    });
};

var saveParams = {
    var f;
    if(File.exists(machine[\stateDir]).not, { File.mkdir(machine[\stateDir]) });
    f = File(paramFile, "w");
    if(f.isOpen, { f.write(paramStore.asCompileString); f.close },{
        "[params] cannot write %".format(paramFile).warn;
    });
};

var restoreParams = { |d|
    (paramStore[d.index.asSymbol] ? ()).keysValuesDo { |k, v|
        if(d.params.includesKey(k), { d.params.put(k, v.asFloat.clip(0, 1)) });
    };
};

// The one place a param changes. Unknown device or unknown key is
// ignored rather than invented, so OSC cannot grow the model.
var setParam = { |port, name, value|
    var d = ~devices.at(port);
    var key = name.asSymbol;
    if(d.notNil and: { d.params.includesKey(key) }, {
        var v = value.asFloat.clip(0, 1);
        d.params.put(key, v);
        paramStore[d.index.asSymbol] = (paramStore[d.index.asSymbol] ? ()).put(key, v);
        paramsDirty = true;
        NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/paramIs", port, key.asString, v);
    });
};

//------------------------------------------------------------
var addDevice = { |ip,port,index|

    var configPattern = "/%/Config".format(index);
    var configListener = OSCFunc({ |msg2, time2, addr2, recvPort2|
        var a = msg2.keep(-4)/255;
        a = (a.keep(3) * (255 / a.keep(3).maxItem)).round ++ a.drop(3);
        d.color = Color.fromArray(a/255);
        postf("Device connected [%]: % : %\n", index, ip, port);
        // "CONFIG % %".format(index,d.color).postln;
        {
            var n = NetAddr.new("127.0.0.1", 57120);
            n.sendMsg("/airkit/addDevice", port);
        }.defer;
    }, configPattern).oneShot;

    var d = Event.new(proto:deviceProto);

    // init model
    d.ip = ip;
    d.port = port;
    d.index = index;
    d.listeners = Event.new(proto:listenersProto);
    d.sensors =  Event.new(proto:sensorsProto);
    d.params = Event.new(proto:paramsProto);
    d.params.volume =  0.5;
    d.params.sensitivity =  0.5;
    restoreParams.(d);
    d.sensors.digiInEvent = Array.fill(4,0); // must allocate for each instance

    // prefill colour     
    d.color = [Color.red,Color.yellow,Color.green,Color.blue].choose;

    // global storage : port is the key
    ~devices.put(port,d);
	NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", port, 0);

    // start listening to device data
    addOSCDeviceListeners.(d);

    NetAddr.new(ip,port-index+1).sendMsg("/Config/GetConfig", 57120);
    
    d // return the device
};

//------------------------------------------------------------
// Listen out for devices sending data
//------------------------------------------------------------
var startOSCListening = {

    var patternBase = "/%/" ++ oscMessageTag;

    // listen for data and if found, add airware virtual device and stop listening
    numAirwareVirtualDevices.do({|i|
        var pattern = patternBase.format(i+1);

        airstickListeners = airstickListeners.add( OSCFunc({ |msg, time, addr, recvPort|
            {
                if(oscThru, { outAddr.sendMsg(*msg) });

                if(~devices.at(addr.port+i) == nil,{
                    var d = addDevice.(addr.ip,addr.port+i,i+1);
                    postf("device [%] auto detected\n", d.index);
                });
            }.defer;
        }, pattern));
    });
};

//------------------------------------------------------------
var stopOSCListening = {
    airstickListeners.do({|obj|
        postf("free : %\n", obj);
        obj.free;
    });
};

//------------------------------------------------------------
// Global Storage
//------------------------------------------------------------

~devices = Dictionary();

loadParams.();

//------------------------------------------------------------
// Public, like /airkit/mute : the panel, the webapp and MIDI all drive
// params through here. OSCdef so re-running main.sc replaces this rather
// than stacking a second responder.
OSCdef(\airkitParam, { |msg|
    setParam.(msg[1].asInteger, msg[2], msg[3]);
}, '/airkit/param');

// A drag writes at mouse rate; the SD card should not. Coalesce and
// flush at most every couple of seconds. Kept in topEnvironment so
// re-running main.sc stops the old routine instead of orphaning it.
~paramFlusher !? { ~paramFlusher.stop };
~paramFlusher = Routine({
    loop {
        2.wait;
        if(paramsDirty, { paramsDirty = false; saveParams.() });
    }
}).play(AppClock);

//------------------------------------------------------------
OSCFunc({ |msg, time, addr, recvPort|
    oscThru = msg[1].asBoolean;
},'/airkit/oscThru', NetAddr("127.0.0.1", NetAddr.langPort));

OSCFunc({ |msg, time, addr, recvPort|
    startOSCListening.();
},'/airkit/startOSCListening', NetAddr("127.0.0.1", NetAddr.langPort)).oneShot;

OSCFunc({ |msg, time, addr, recvPort|
    stopOSCListening.();
},'/airkit/stopOSCListening', NetAddr("127.0.0.1", NetAddr.langPort)).oneShot;

OSCFunc({ |msg, time, addr, recvPort|
    var devicePort = msg[1].asInteger;
    calibrateDevice.value(devicePort);
},'/airkit/calibrate', NetAddr("127.0.0.1", NetAddr.langPort));

//------------------------------------------------------------
OSCFunc({ |msg, time, addr, recvPort|
    ~devices.keys.asArray.postln;
    addr.sendMsg("/airkitremote/devices", ~devices.keys.asArray);
},'/airkit/remote/devices');
