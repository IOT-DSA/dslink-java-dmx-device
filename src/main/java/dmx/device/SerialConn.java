package dmx.device;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.io.serial.SerialParameters;
import com.serotonin.io.serial.SerialPortException;
import com.serotonin.io.serial.SerialPortProxy;
import com.serotonin.io.serial.SerialUtils;

public class SerialConn {
	private static final Logger LOGGER = LoggerFactory.getLogger(SerialConn.class);
	
	private Node node;
	private SerialLink link;
	
	int[] channelValues = new int[512];
	private int position = -5;
	private int frameSize = 0;
	
	final Set<DmxDevice> devices = new HashSet<DmxDevice>();
	
	// Status node. Communicates whether the port is open or closed.
	private Node statnode = null;
	
	// This connection's serial port
	private SerialPortProxy serialPort = null;
	
	// When my node is subscribed to, this refers to the thread that listens on
	// my serial port (if it's open). When not subscribed, this is null. 
	private ScheduledFuture<?> future;
	
	// The bytes that signify the start and end of a message.
	static int DMX_START = 0x7e;
	static int DMX_END = 0xe7;
	static int DMX_SEND_PACKET = 6;
	
	SerialConn(SerialLink link, Node node) {
		this.link = link;
		this.node = node;
		this.link.conns.add(this);
	}
	
	void restoreLastSession() {
		node.clearChildren();
		init();
	}
	
	void init() {
		if (statnode == null) {
			statnode = node.createChild("Status").setValueType(ValueType.STRING).setValue(new Value("Initializing")).build();
		} else {
			statnode.setValue(new Value("Initializing"));
		}
		
		makeEditAction();
		makeRemoveAction();
		
		makeAddDeviceAction();
		
		connect();
	}
	
	/* Open the serial port and set up actions which should be available while the
	 * port is open. */
	private void connect() {
		if (serialPort != null) return;
		
		SerialParameters serialParams = new SerialParameters();

        serialParams.setCommPortId(node.getAttribute("Serial Port").getString());
        serialParams.setBaudRate(node.getAttribute("Baud Rate").getNumber().intValue());
        serialParams.setDataBits(node.getAttribute("Data Bits").getNumber().intValue());
        serialParams.setStopBits(node.getAttribute("Stop Bits").getNumber().intValue());
        serialParams.setParity(node.getAttribute("Parity").getNumber().intValue());
        
        try {
			serialPort = SerialUtils.openSerialPort(serialParams);
		} catch (SerialPortException e) {
			LOGGER.debug("", e);
			serialPort = null;
		}
        
        if (serialPort != null) {
        	subscribe();
        	statnode.setValue(new Value("Connected"));
        	node.removeChild("connect");
        	makeDisconnectAction();
        } else {
        	statnode.setValue(new Value("Failed to Connect"));
        	node.removeChild("disconnect");
        	node.removeChild("send message");
        	makeConnectAction();
        }
	}
	
	/* Close the serial port and set up actions which should be available while the port
	 * is closed. Also discard any bytes that were read since the last complete message. */
	private void disconnect() {
		unsubscribe();
		if (serialPort == null) return;
		try {
			SerialUtils.close(serialPort);
		} catch (SerialPortException e) {
			LOGGER.debug("", e);		
		}
		serialPort = null;
		
		statnode.setValue(new Value("Disconnected"));
    	node.removeChild("disconnect");
    	node.removeChild("send message");
    	makeConnectAction();
		
	}
	
	
	/* Read and handle all available bytes from the serial port. Once no bytes are
	 * available, wait half a second and check for more. (If serial port is closed,
	 * just wait until it is open) */
	private void subscribe() {
		if (future != null) return;
		ScheduledThreadPoolExecutor stpe = Objects.getDaemonThreadPool();
		future = stpe.scheduleWithFixedDelay(new Runnable() {
			public void run() {
				readWhileAvailable();
			}
		}, 0, 500, TimeUnit.MILLISECONDS);
	}
	
	/* Stop reading (or trying to read) bytes from the serial port. Discard any bytes
	 * that were read since the last complete message. */
	private void unsubscribe() {
		position = -5;
		if (future == null) return;
		future.cancel(false);
		future = null;
	}
	
	/* Read and handle all available bytes from the serial port. */
	private void readWhileAvailable() {
		if (serialPort == null) return;
		try {
			while (serialPort.getInputStream().available() > 0) {
				int b = serialPort.getInputStream().read();
				if (position == -5) {
					if (b == DMX_START) position += 1;
				} else if (position == -4) {
					if (b == DMX_SEND_PACKET) position += 1;
					else position = -5;
				} else if (position == -3) {
					frameSize = b;
					position += 1;
				} else if (position == -2) {
					frameSize = (b << 8) | frameSize;
					position += 1;
				} else if (position == -1) {
					position += 1;
				} else if (position >= 0 && position < frameSize-1) {
					channelValues[position] = b;
					position += 1;
				} else if (position == frameSize-1) {
					updateDevices();
					if (b == DMX_END) {
						position = -5;
						frameSize = 0;
					}
				}
			}
		} catch (IOException e) {
			LOGGER.debug("", e);
		}
	}
	
	private void updateDevices() {
		for (DmxDevice device: devices) {
			device.update();
		}
	}
	
	/* Create the action that allows editing the connection's parameters. */
	void makeEditAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleEdit(event);
			}
		});
		act.addParameter(new Parameter("Name", ValueType.STRING, new Value(node.getName())));
		
		Set<String> portids = link.getCOMPorts();
		portids.add(node.getAttribute("Serial Port").getString());
		act.addParameter(new Parameter("Serial Port", ValueType.makeEnum(portids), node.getAttribute("Serial Port")));
		act.addParameter(new Parameter("Serial Port (manual entry)", ValueType.STRING));
		
		act.addParameter(new Parameter("Baud Rate", ValueType.NUMBER, node.getAttribute("Baud Rate")));
		act.addParameter(new Parameter("Data Bits", ValueType.NUMBER, node.getAttribute("Data Bits")));
		act.addParameter(new Parameter("Stop Bits", ValueType.NUMBER, node.getAttribute("Stop Bits")));
		act.addParameter(new Parameter("Parity", ValueType.NUMBER, node.getAttribute("Parity")));
		
		Node anode = node.getChild("edit");
		if (anode == null) node.createChild("edit").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	/* Handle an invocation of the edit action, restarting the connection to the
	 * serial port with the new parameters. */
	private void handleEdit(ActionResult event) {
		String name = event.getParameter("Name", ValueType.STRING).getString();
		String com;
		Value customPort = event.getParameter("Serial Port (manual entry)");
		if (customPort != null && customPort.getString() != null && customPort.getString().trim().length() > 0) {
			com = customPort.getString();
		} else {
			com = event.getParameter("Serial Port").getString();
		}
		int baud = event.getParameter("Baud Rate", ValueType.NUMBER).getNumber().intValue();
		int dbits = event.getParameter("Data Bits", ValueType.NUMBER).getNumber().intValue();
		int sbits = event.getParameter("Stop Bits", ValueType.NUMBER).getNumber().intValue();
		int parity = event.getParameter("Parity", ValueType.NUMBER).getNumber().intValue();
		
		if (!node.getName().equals(name)) {
			Node cnode = node.getParent().createChild(name).build();
			cnode.setAttribute("Serial Port", new Value(com));
			cnode.setAttribute("Baud Rate", new Value(baud));
			cnode.setAttribute("Data Bits", new Value(dbits));
			cnode.setAttribute("Stop Bits", new Value(sbits));
			cnode.setAttribute("Parity", new Value(parity));
			SerialConn sc = new SerialConn(link, cnode);
			remove();
			sc.init();
		} else {
			node.setAttribute("Serial Port", new Value(com));
			node.setAttribute("Baud Rate", new Value(baud));
			node.setAttribute("Data Bits", new Value(dbits));
			node.setAttribute("Stop Bits", new Value(sbits));
			node.setAttribute("Parity", new Value(parity));
			
			disconnect();
			init();
		}
	}
	
	/* Make the action that closes this connection and removes my node. */
	private void makeRemoveAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				remove();
			}
		});
		Node anode = node.getChild("remove");
		if (anode == null) node.createChild("remove").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	/* Close my serial port and delete my node. */
	private void remove() {
		disconnect();
		node.clearChildren();
		node.getParent().removeChild(node);
	}
	
	/* Make the action that opens the serial port. */
	private void makeConnectAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				connect();
			}
		});
		Node anode = node.getChild("connect");
		if (anode == null) node.createChild("connect").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	/* Make the action that closes the serial port. */
	private void makeDisconnectAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				disconnect();
			}
		});
		Node anode = node.getChild("disconnect");
		if (anode == null) node.createChild("disconnect").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	private void makeAddDeviceAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleAddDevice(event);
			}
		});
		act.addParameter(new Parameter("Name", ValueType.STRING));
		act.addParameter(new Parameter("Base Address", ValueType.NUMBER, new Value(0)));
		Node anode = node.getChild("add device");
		if (anode == null) node.createChild("add device").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	private void handleAddDevice(ActionResult event) {
		String name = event.getParameter("Name", ValueType.STRING).getString();
		int baseAddr = event.getParameter("Base Address", ValueType.NUMBER).getNumber().intValue();
		
		Node dnode = node.createChild(name).build();
		dnode.setAttribute("Base Address", new Value(baseAddr));
		
		DmxDevice dev = new DmxDevice(this, dnode);
		dev.init();
	}
	

}
