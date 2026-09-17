//------------------------------------------------------------
// machine.scd
//
// Per-machine settings. Every value that differs between one AirKit and
// the next lives in machines/<name>.scd, so no two kits ever edit the
// same file and a merge has nothing to reconcile. Branches stop being
// how machines differ.
//
// Identification is automatic - a kit works out which file is its own.
// Each machine file declares its own identifiers, so there is no shared
// machine->identity table for two kits to fight over. In order:
//
//   1. \apAddr    - the kit's own AP address. Each Pi is the router on its
//                   own network at a fixed .1, so this is the strongest
//                   signal available and it survives a board swap.
//   2. \macs      - hardware addresses, if apAddr is unavailable
//   3. \hostnames - for machines that are not the router (the laptop)
//   4. platform   - the single file claiming this \platform, if only one
//   5. nothing    - posted loudly, built-in defaults
//
// Paths are derived from this file's own location, so a clone anywhere
// works - nothing here knows about ~/Develop.
//
// Require caches by canonical path within one evaluation, so main.sc,
// oscController, personalityController and systemView all share the one
// resolved Event.
//
// Returns the config Event.
//------------------------------------------------------------

var root = thisProcess.nowExecutingPath.dirname.dirname;
var dir = root +/+ "machines";

var platform = thisProcess.platform.name;
var isOSX = platform == \osx;

var host = "hostname".unixCmdGetStdOut.stripWhiteSpace;
var shortHost = host.split($.)[0];

// Both commands emit one token per line or per space depending on platform,
// so normalise before splitting.
var readTokens = { |cmd|
	cmd.unixCmdGetStdOut.replace("\n", " ").split($ )
		.collect(_.stripWhiteSpace).reject(_.isEmpty)
};

// every address this machine holds - on a Pi acting as its own router
// that includes the AP's fixed .1
var myAddrs = readTokens.(if(isOSX,
	{ "ifconfig -a | awk '/inet /{print $2}'" },
	{ "hostname -I" }));

var myMacs = readTokens.(if(isOSX,
	{ "ifconfig -a | awk '/ether/{print $2}'" },
	{ "cat /sys/class/net/*/address 2>/dev/null" })).collect(_.toLower);

// The defaults are what an unrecognised machine boots with, so they are
// derived from the platform rather than fixed - a Pi that nothing claims
// still comes up borderless and fullscreen, as it did before this file.
var defaults = (
	label: "unknown",
	user: "unknown",
	apAddr: nil,
	macs: [],
	hostnames: [],
	platform: nil,

	outAddr: ["127.0.0.1", 5005],
	numDevices: 5,

	list: "list_dev.sc",

	secs: 0.01,
	latency: if(isOSX, 0.03, 0.1),

	// Server audio. blockSize is the control-rate period and sets the
	// resolution of every .kr detector. hardwareBufferSize is the CoreAudio /
	// ALSA buffer and dominates round-trip latency; nil leaves it to the
	// driver, which on macOS means 512.
	blockSize: 128,
	hardwareBufferSize: nil,

	border: isOSX,
	fullScreen: isOSX.not,

	listsDir: root +/+ "lists/",
	personalityDir: root +/+ "personalities/",
	versionPath: root +/+ "VERSION",

	// Runtime state the kit writes for itself. Deliberately NOT a
	// release_dir: an update never rmtree's it, so it survives upgrades.
	// No trailing slash - callers use +/+.
	stateDir: root +/+ "state"
);

var readMachine = { |path|
	var func = thisProcess.interpreter.compileFile(path);
	var ev = func !? { func.value };
	if(ev.isKindOf(Event).not, {
		"[machine] %.scd did not return an Event - skipped"
			.format(PathName(path).fileNameWithoutExtension).warn;
		ev = nil;
	});
	ev
};

var loaded = (dir +/+ "*.scd").pathMatch
	.collect({ |p| (path: p, name: PathName(p).fileNameWithoutExtension, cfg: readMachine.(p)) })
	.select({ |e| e[\cfg].notNil });

var hit = nil;
var how = nil;
var cfg;

//------------------------------------------------------------

if(loaded.isEmpty, {
	"[machine] no machine files in % - using built-in defaults".format(dir).warn;
});

// includesEqual, not includes : includes compares by identity, so it is
// silently always false for Strings.
hit = loaded.detect({ |e|
	e[\cfg][\apAddr].notNil and: { myAddrs.includesEqual(e[\cfg][\apAddr]) }
});
if(hit.notNil, { how = "AP address " ++ hit[\cfg][\apAddr] });

if(hit.isNil, {
	hit = loaded.detect({ |e|
		(e[\cfg][\macs] ? []).any({ |m| myMacs.includesEqual(m.asString.toLower) })
	});
	if(hit.notNil, { how = "MAC" });
});

if(hit.isNil, {
	hit = loaded.detect({ |e|
		(e[\cfg][\hostnames] ? []).any({ |h| (h == host) or: { h == shortHost } })
	});
	if(hit.notNil, { how = "hostname " ++ host });
});

if(hit.isNil, {
	var samePlatform = loaded.select({ |e| e[\cfg][\platform] == platform });
	if(samePlatform.size == 1, {
		hit = samePlatform[0];
		how = "the only % machine file".format(platform);
	});
});

if(hit.isNil, {
	"".postln;
	"*** [machine] UNKNOWN MACHINE - running built-in defaults ***".postln;
	"    hostname  : %".format(host).postln;
	"    platform  : %".format(platform).postln;
	"    addresses : %".format(myAddrs.join(", ")).postln;
	"    MACs      : %".format(myMacs.join(", ")).postln;
	"    No machines/*.scd claims any of the above.".postln;
	"    Fix: set \\apAddr to this kit's AP address in its machine file,".postln;
	"         or add a hostname or MAC from the lists above.".postln;
	"".postln;
});

cfg = defaults.copy;
if(hit.notNil, { cfg.putAll(hit[\cfg]) });

if(cfg[\list].isKindOf(String), { cfg[\list] = cfg[\list] ! cfg[\numDevices] });

"[machine] % (%) via % : % devices : roster %".format(
	cfg[\label], cfg[\user], how ? "nothing - defaults",
	cfg[\numDevices],
	cfg[\list].asSet.asArray.join(", ")
).postln;

cfg
