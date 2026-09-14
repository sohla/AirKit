// Rosters and paths come from machines/<name>.scd - see machine.scd.
var machine = Require("machine.scd");

var listsDir = machine[\listsDir];
var personalityDir = machine[\personalityDir];
var defaultLists = machine[\list];
// =======
// // var list = "list_ITR_Mel.sc";
// // var list = "list_SiS_Alessio.sc";
// // var list = "list_glenroy.sc";
// // var list = "list_dev.sc";
// // var list = "list_NZ.sc";
// // var list = "list_ITR_Nic.sc";
// // var list = "list_yourDNA25.sc";
// // var list = "list_workshop1.sc";

// // var list = "list_alon26.sc";
// // var list = "list_yourDNA26.sc";
// var list = "list_BtB.sc";
// // var list = "list_glenroy.sc";
// // var list = "list_Mel_26.sc";
// var defaultLists = list!9;


// var defaultLists = [
//     "list_NZ_Mel.sc",
//     "list_NZ_Mel.sc",

//     "list_NZ_Suz.sc",
//     "list_NZ_Suz.sc",

//     "list_NZ_Suz.sc",
// ];

// var defaultLists = [
//     "list_MIM25_James.sc",
//     "list_MIM25_Caz.sc",
//     "list_MIM25_Tim.sc",
//     "list_MIM25_Matt.sc",
//     "list_MIM25_Heather.sc",
// ];



// var defaultLists = [
//     "list_ITR_Mel.sc",
//     "list_ITR_Nic.sc",
//     "list_ITR_Brenton.sc",
//     "list_ITR_Alessio.sc",
//     "list_ITR_Mel.sc",
// ];
// >>>>>>> 06b7559 (p-files fun)

var com = (
    \root: 0,
    \dur: 1,
    \accelMass: 0,
    \rrateMass: 0,
);

//------------------------------------------------------------
//
//------------------------------------------------------------
var loadPersonalityList = {|i|
    var path = PathName.new(listsDir++defaultLists[i]);
    var file = File.new(path.asAbsolutePath,"r");
    var str = file.readAllString;
    // var time = File.mtime(path.asAbsolutePath);
    // time.postln;
    interpret(str) // returns array of personality files
};

var interpretPersonality = {|d|

    // Resolved HERE, in our own environment, and captured lexically.
    // ~visuals lives in topEnvironment (set by visualCore.scd), but the
    // functions below run under d.env.use{}, and a personality Environment
    // is built by Environment.make with a nil parent/proto - so a ~visuals
    // lookup from inside them silently returns nil.
    var visuals = ~visuals;

    var path = PathName.new(personalityDir++d.name++".sc");
    var file = File.new(path.asAbsolutePath,"r");
    var str = file.readAllString;

    // after adding personality to an Environment, add useful functions to be used by anyone
    var env = Environment.make {
        ~filePath = path.asAbsolutePath;
        ~fileTime = File.mtime(path.asAbsolutePath);
        // ~group = Group.new;
        ~model = (
            \com: com,
            \name: d.name,
            \ptn: Array.fill(16,{|i|i=90.rrand(65).asAscii.toLower}).join(),
            \rrateMass: 0,
            \rrateMassFiltered: 0,
            \rrateMassFilteredAttack: 0.9,
            \rrateMassFilteredDecay: 0.7,

            // \rrateMassThreshold: 0.21, //use for isMoving
            // \rrateMassThresholdSpec: ControlSpec(0.07, 0.4, \lin, 0.01, 0.21),

            \accelMass: 0,
            \accelMassFiltered: 0,
            \accelMassFilteredAttack: 0.9,
            \accelMassFilteredDecay: 0.7,

            // \accelMassAmpThreshold: 2.0,
            // \accelMassThresholdSpec: ControlSpec(0.4, 3.0, \lin, 0.1, 2.0),

            \gyroXFiltered: 0,
            \gyroYFiltered: 0,
            \gyroZFiltered: 0,
            \gyroFilteredAttack: 0.9,
            \gyroFilteredDecay: 0.7,


            // \isHit: false,
            // \isMoving: true,
            // \accelMassAmp: 0.0,

        );
        ~device = d;

        //------------------------------------------------------------
        // register a custom shape for this device's visual events.
        // Namespaced per device, so two performers can each have their
        // own \blob. Reloaded with the file like everything else, which is
        // why this - not vdefLib.scd - is where you live-code a shape.
        //
        // Registering a name that vdefLib.scd already defines SHADOWS it for
        // this device only : lookup is device-local, then the library.
        //
        // The core has ALREADY applied position, rotation, Pen.width and
        // both pen colours before your function runs. It hands you
        //   ev : the raw event    (startSize: endSize: fill: modulation: ...)
        //   c  : resolved values  (pos: size: width: color: rotation:
        //                          normTime: elapsed: now: bounds: view:
        //                          render: draw:)
        // NB read c with brackets - c[\size], NOT c.size. Set declares
        // `var <size`, so the dot form returns the Event's item count.
        //
        // Two contracts, chosen by what you return:
        //
        // 1. POINTS FUNC - return an Array of Points. \modulation, \closed
        //    and \fill are then applied for you. Every entry in vdefLib.scd
        //    is one of these.
        //
        //   ~vdef.(\blob, { |ev, c|
        //       Array.fill(32, { |i|
        //           var a = i / 32 * 2pi;
        //           c[\pos] + Polar(c[\size], a).asPoint
        //       })
        //   });
        //
        // 2. DRAW FUNC - for a mark that is several sub-paths. END IT WITH
        //    nil : .do returns the collection it iterated, and an Array of
        //    numbers would be drawn as coordinates near the origin.
        //    Do NOT call Pen yourself; call
        //    c[\render], which is the same pipeline bound to this event, so
        //    each sub-path still gets \modulation, \closed and \fill:
        //
        //      c[\render].(points, widthScale, alphaScale, closed)
        //
        //    closed overrides \closed for that sub-path only - it defaults
        //    to the event's, which is true, so an open shape needs false.
        //
        //    The scales default to 1 and multiply the event's own width and
        //    alpha - absolutes still come from the event.
        //
        //    c[\draw] does the same but sources the points from vdefLib,
        //    so a mark can reuse library shapes as its parts:
        //
        //      c[\draw].(\circle, (pos: p, size: r), wScale, aScale, closed)
        //
        //   ~vdef.(\pair, { |ev, c|
        //       c[\render].([ c[\pos], c[\pos] + (c[\size] @ 0) ], 1, 0.5);
        //       c[\render].([ c[\pos], c[\pos] - (c[\size] @ 0) ], 1, 1.0);
        //   });
        //
        // Pen is for output a point list cannot express - fills with holes,
        // text, images. Reimplementing the API by hand is how \fill and
        // \modulation quietly go missing.
        //
        // then emit  (type: \customVisualEvent, shape: \blob, viewID: d.port, ...)
        ~vdef = { |name, func|
            if(visuals.notNil, { visuals.vdef(d.port, name, func) });
            name
        };

        //------------------------------------------------------------
        // frame rate of rout
        ~secs = machine[\secs];

        //------------------------------------------------------------
        // process data->model
        ~processDeviceData = {|d|
            ~model.accelMass = d.sensors.accelEvent.sumabs * 0.33; // scale it
            ~model.rrateMass = d.sensors.rrateEvent.sumabs;
            ~model.accelMassFiltered = ~smooth.(
                ~model.accelMass,
                ~model.accelMassFiltered,
                ~model.accelMassFilteredAttack,
                ~model.accelMassFilteredDecay
            );
            ~model.rrateMassFiltered = ~smooth.(
                ~model.rrateMass,
                ~model.rrateMassFiltered,
                ~model.rrateMassFilteredAttack,
                ~model.rrateMassFilteredDecay
            );
            // normalise filtered gyro
            ~model.gyroXFiltered = ~smooth.(
                d.sensors.gyroEvent.x / pi,
                ~model.gyroXFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
            );
            ~model.gyroYFiltered = ~smooth.(
                d.sensors.gyroEvent.y / pi.half,
                ~model.gyroYFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
            );
            ~model.gyroZFiltered = ~smooth.(
                // (d.sensors.gyroEvent.z / pi).fold(-0.5,0.5), // nice to have
                d.sensors.gyroEvent.z / pi,
                ~model.gyroZFiltered,
                ~model.gyroFilteredAttack,
                ~model.gyroFilteredDecay
            );
        };

        //------------------------------------------------------------
        ~play = {
            postf("play : % \n",~model.name);
            Pdef(~model.ptn).play(0.125);

        };

        ~stop = {
            postf("stop : % \n",~model.name);
            Pdef(~model.ptn).stop();
        };

        //------------------------------------------------------------
        ~init = {
            postf("init : % [%] \n",~model.name, ~model.ptn);
        };

        //------------------------------------------------------------
        //------------------------------------------------------------
        ~deinit = {
            postf("deinit : % [%] \n",~model.name, ~model.ptn);
        };

        //------------------------------------------------------------

        //------------------------------------------------------------
        interpret(str);
        //------------------------------------------------------------

        ~smooth= {|input,history, attack=0.5, decay=0.05|
            var coeff = attack;
            if(history > input, {coeff = decay});
            (coeff * input + ((1 - coeff) * history))
            // history + coeff * (input - history)
        };

        ~slope = {|input,history|
            history - input
        };
    };

    env
};

//------------------------------------------------------------
//
//------------------------------------------------------------

var createProcRout = {|d|
    Routine {
        loop{
            d.env.use{
                if(~fileTime != File.mtime(~filePath),{
                    ~fileTime = File.mtime(~filePath);
                    "[file saved]".postln;
                    NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/loadPersonality", d.port, d.personality);
                });
                if(d.enabled == true,{
                    // pre-process data -> personality model
                    ~processDeviceData.(d);
                    //post process : make changes in personlity file
                    ~next.(d);
                });
                (~secs.()).yield;
            };
        };
    }
};
//------------------------------------------------------------
// s
//------------------------------------------------------------

var loadPersonality = { |port, index|

    var d = ~devices.at(port);
    var devicePersonalityList = loadPersonalityList.(d.index-1);

    // d.personality = index;
    // stop personality
    d.procRout.stop;
    d.procRout.free;

    if(d.env != nil,{ d.env.use{
        Routine{
            s.sync;
            ~deinit.();
            s.sync;
            // ~group.free;
            s.sync;
        }.play;
    }});

    // change device to new personality
    index = index.mod(devicePersonalityList.size-1);
    d.name = devicePersonalityList[index];
    "loading personality: %".format(d.name).postln;

    NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/personalityName", d.port, d.name);
    if(~visuals.notNil, {
        ~visuals.clearVdefs(d.port);
        ~visuals.clearEvents(d.port);
    });
    d.env = interpretPersonality.(d);
    // ["TODO",d.enabled].postln;
    d.env.use{
        Routine{
            s.sync;
            ~init.(d);
            s.sync;
        }.play;
    };

    d.procRout = createProcRout.(d);
    d.procRout.reset.play(AppClock);

    ~devices.put(d.port,d);

    d

};
//------------------------------------------------------------
//
//------------------------------------------------------------

var unLoadPersonality = { |port, index|

    var d = ~devices.at(port);
    var devicePersonalityList = loadPersonalityList.(d.index-1);

    // d.personality = index;
    // stop personality
    d.procRout.stop;
    d.procRout.free;

    if(d.env != nil,{ d.env.use{
        Routine{
            s.sync;
            ~deinit.();
            s.sync;
            // ~group.free;
            // s.sync;
        }.play;
    }});
    d
};

//------------------------------------------------------------
//
//------------------------------------------------------------

var reLoadPersonality = { |port, index|

    var d = ~devices.at(port);
    // var devicePersonalityList = loadPersonalityList.(d.index-1);

    // change device to new personality
    // index = index.mod(devicePersonalityList.size-1);
    // d.name = devicePersonalityList[index];
    // "loading personality: %".format(d.name).postln;

    // NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/personalityName", d.port, d.name);
    if(~visuals.notNil, {
        ~visuals.clearVdefs(d.port);
        ~visuals.clearEvents(d.port);
    });
    d.env = interpretPersonality.(d);
    // ["TODO",d.enabled].postln;
    d.env.use{
        Routine{
            s.sync;
            ~init.(d);
            s.sync;
        }.play;
    };

    d.procRout = createProcRout.(d);
    d.procRout.reset.play(AppClock);

    ~devices.put(d.port,d);

    d

};

//------------------------------------------------------------
//
//------------------------------------------------------------
~devices = Dictionary();

OSCFunc({ |msg, time, addr, recvPort|
    loadPersonality.(msg[1], msg[2]);
},'/airkit/loadPersonality'); //* not having an NetAddr makes this public ie. external control

OSCFunc({ |msg, time, addr, recvPort|
    unLoadPersonality.(msg[1], msg[2]);
},'/airkit/unLoadPersonality', NetAddr("127.0.0.1", NetAddr.langPort));

OSCFunc({ |msg, time, addr, recvPort|
    reLoadPersonality.(msg[1], msg[2]);
},'/airkit/reLoadPersonality', NetAddr("127.0.0.1", NetAddr.langPort));

// OSCFunc({ |msg, time, addr, recvPort|
//     NetAddr.new("127.0.0.1", 57120).sendMsg("/airkit/personalityName", msg[1], ~devices.at(msg[1]).name);
// },'/airkit/getPersonalityName', NetAddr("127.0.0.1", NetAddr.langPort));
