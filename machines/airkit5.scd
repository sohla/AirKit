// AirKit5 — SensiLab
// AP: 192.168.250.1 (this kit is the router on its own network)

(
	label: "AirKit5",
	user: "SensiLab",

	apAddr: "192.168.250.1",
	macs: ["d8:3a:dd:ca:e2:7e","d8:3a:dd:ca:e2:7f"],
	platform: \linux,

	numDevices: 9,

	list: "list_dev.sc",

	secs: 0.01,
	latency: 0.1,

	border: false,
	fullScreen: true
)
