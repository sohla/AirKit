// AirKit2 — Mel
// AP: 192.168.200.1 (this kit is the router on its own network)

(
	label: "AirKit2",
	user: "Mel",

	apAddr: "192.168.200.1",
	macs: ["d8:3a:dd:ca:e3:3e","d8:3a:dd:ca:e3:3f"],
	platform: \linux,

	numDevices: 9,

	// list: unconfirmed — falls back to list_dev.sc until set.
	list: "list_Mel_26.sc",
	// list: "list_alon26.sc",
	// list: "list_ITR_Nic.sc",
	// list: "list_yourDNA26.sc",
	// list: "list_BtB.sc",
	// list: "list_glenroy.sc",

	secs: 0.01,
	latency: 0.1,

	border: false,
	fullScreen: true
)
/*

What to do

Cold boot the Pi with all sticks powered, then run this twice — once while broken, once after the fix-it restart:

S=/tmp/claude-1000/-home-pi-Develop-SuperCollider-Projects-AirKit/051f346a-ba80-4b16-8fdd-e127db4ea627/scratchpad

sudo $S/snapshot.sh A-after-boot        # sticks NOT connecting
# now hit the Network button in AirKit, wait for a stick to come up
sudo $S/snapshot.sh B-after-nmrestart   # sticks working

It writes /tmp/aksnap-A-after-boot.txt and /tmp/aksnap-B-after-nmrestart.txt. It deliberately does not scan, since scanning pulls the AP off-channel and would corrupt the measurement.

Then tell me and I'll diff them. Whatever differs between A and B is the bug — no more hypotheses from me. That diff will either name it outright or narrow it to a handful of lines.
*/
