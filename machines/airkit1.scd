// AirKit1 — MiM
// AP: 192.168.100.1 (this kit is the router on its own network)

(
	label: "AirKit1",
	user: "MiM",

	apAddr: "192.168.100.1",
	macs: ["d8:3a:dd:ca:e3:d4","d8:3a:dd:ca:e3:d5"],
	platform: \linux,

	numDevices: 5,

	list: "list_yourDNA26.sc",

	secs: 0.01,
	latency: 0.1,

	border: false,
	fullScreen: true
)
