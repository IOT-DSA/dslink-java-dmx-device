# dslink-java-dmx-device
DSLink that can be controlled through a serial connection by a DMX-512 master

## Usage
There are 512 1-byte channels in the DMX protocol. When adding a device, specify the base address for the channels that the device will use. When adding a component to a device, specify the offsets from that base address for any channels used by that component. For instance, if I add a device with base address 50 and add to it a linear component with channel offset 3, that component will be controlled by DMX channel 53.

A linear component reflects the 8-bit unsigned integer value of exactly one DMX channel.

An RGB component uses 3 channels define a color value.

A multistate component uses one channel, and translates its value to some state, represented as a string. The mapping of states to value ranges is specified by the "Value Mappings" parameter, which should be set to the string representation of a JSON Object mapping state names value ranges (Value ranges should be 2-element integer arrays).

Example of a Value Mapping: '{"Open": [0, 35], "Red": [36, 70], "Cyan": [71, 105], "Green": [106, 140], "Yellow": [141, 175], "Blue": [176, 210], "Magenta": [211, 255]}'
