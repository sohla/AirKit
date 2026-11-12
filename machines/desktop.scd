// Steph's laptop. Not a router, so it identifies by hostname.
// MU00157721X is an institution-managed asset name and may change on
// reimage; add the replacement here if it does.

(
	label: "desktop",
	user: "Steph",

	hostnames: ["MU00157721X"],
	macs: [],
	platform: \osx,

	outAddr: ["192.168.70.211", 5005],
	numDevices: 10,

	// list: "list_BtB_Dev.sc",
	// list: "list_glenroy.sc",
	// list: "list_yourDNA26.sc",	
	// list: "list_circus26.sc",

	list: [
		"list_BtB_Nic.sc",
		"list_BtB_Mel.sc",
		"list_BtB_Nic.sc",
		"list_BtB_Mel.sc",
		"list_BtB_Mel.sc",
		"list_BtB_Nic.sc",
		"list_BtB_Mel.sc",
		"list_BtB_Nic.sc",
		"list_BtB_Nic.sc",
		"list_BtB_Mel.sc",
	],

	secs: 0.01,
	latency: 0.03,

	blockSize: 64,
	hardwareBufferSize: 64,
	numInputs: 2,
	numOutputs: 4,

	border: true,
	fullScreen: false,
	bgColor: Color.white,
	screenProbe: \osx,

	drumInput: false
)
