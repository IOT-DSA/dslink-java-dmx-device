package dmx.device;

import java.util.HashSet;
import java.util.Set;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonObject;

public class DmxDevice {
	
	SerialConn conn;
	private Node node;
	final Set<DmxComponent> components = new HashSet<DmxComponent>();
	
	int baseAddress;
	private int lowestUnused = 0;
	
	DmxDevice(SerialConn conn, Node node) {
		this.conn = conn;
		this.node = node;
		
		this.conn.devices.add(this);
	}
	
	void init() {
		this.baseAddress = node.getAttribute("Base Address").getNumber().intValue();
		
		makeEditAction();
		makeRemoveAction();
		makeAddLinearComponentAction();
		makeAddRgbComponentAction();
		makeAddMultistateComponentAction();
	}

	void update() {
		for (DmxComponent component: components) {
			component.update();
		}
	}
	
	private void makeEditAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleEdit(event);
			}
		});
		act.addParameter(new Parameter("Base Address", ValueType.NUMBER, new Value(baseAddress)));
		Node anode = node.getChild("edit");
		if (anode == null) node.createChild("edit").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	private void handleEdit(ActionResult event) {
		this.baseAddress = event.getParameter("Base Address", ValueType.NUMBER).getNumber().intValue();
		
		node.setAttribute("Base Address", new Value(baseAddress));
		
		makeEditAction();
	}
	
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
	
	private void remove() {
		conn.devices.remove(this);
		node.clearChildren();
		node.getParent().removeChild(node);
	}
	
	private void makeAddLinearComponentAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleAddLinearComponent(event);
			}
		});
		act.addParameter(new Parameter("Name", ValueType.STRING));
		act.addParameter(new Parameter("Channel Offset", ValueType.NUMBER, new Value(lowestUnused)));
		Node anode = node.getChild("add linear component");
		if (anode == null) node.createChild("add linear component").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	private void handleAddLinearComponent(ActionResult event) {
		String name = event.getParameter("Name", ValueType.STRING).getString();
		int offset = event.getParameter("Channel Offset", ValueType.NUMBER).getNumber().intValue();
		
		if (offset >= lowestUnused) lowestUnused = offset + 1;
		
		Node cnode = node.createChild(name).setValueType(ValueType.NUMBER).build();
		cnode.setAttribute("Channel Offset", new Value(offset));
		
		LinearComponent lc = new LinearComponent(this, cnode);
		lc.init();
	}
	
	private void makeAddRgbComponentAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleAddRgbComponent(event);
			}
		});
		act.addParameter(new Parameter("Name", ValueType.STRING));
		act.addParameter(new Parameter("Red Channel Offset", ValueType.NUMBER, new Value(lowestUnused)));
		act.addParameter(new Parameter("Green Channel Offset", ValueType.NUMBER, new Value(lowestUnused+1)));
		act.addParameter(new Parameter("Blue Channel Offset", ValueType.NUMBER, new Value(lowestUnused+2)));
		Node anode = node.getChild("add rgb component");
		if (anode == null) node.createChild("add rgb component").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	private void handleAddRgbComponent(ActionResult event) {
		String name = event.getParameter("Name", ValueType.STRING).getString();
		int roffset = event.getParameter("Red Channel Offset", ValueType.NUMBER).getNumber().intValue();
		int goffset = event.getParameter("Green Channel Offset", ValueType.NUMBER).getNumber().intValue();
		int boffset = event.getParameter("Blue Channel Offset", ValueType.NUMBER).getNumber().intValue();
		
		if (boffset >= lowestUnused) lowestUnused = boffset + 1;
		
		Node cnode = node.createChild(name).setValueType(ValueType.STRING).build();
		cnode.setAttribute("Red Channel Offset", new Value(roffset));
		cnode.setAttribute("Green Channel Offset", new Value(goffset));
		cnode.setAttribute("Blue Channel Offset", new Value(boffset));
		
		RgbComponent rc = new RgbComponent(this, cnode);
		rc.init();
	}
	
	private void makeAddMultistateComponentAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleAddMultistateComponent(event);
			}
		});
		act.addParameter(new Parameter("Name", ValueType.STRING));
		act.addParameter(new Parameter("Channel Offset", ValueType.NUMBER, new Value(lowestUnused)));
		act.addParameter(new Parameter("Value Mappings", ValueType.STRING, new Value(new JsonObject().toString())));
		Node anode = node.getChild("add multistate component");
		if (anode == null) node.createChild("add multistate component").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	private void handleAddMultistateComponent(ActionResult event) {
		String name = event.getParameter("Name", ValueType.STRING).getString();
		int offset = event.getParameter("Channel Offset", ValueType.NUMBER).getNumber().intValue();
		String mapStr = event.getParameter("Value Mappings", ValueType.STRING).getString();
		
		if (offset >= lowestUnused) lowestUnused = offset + 1;
		
		Node cnode = node.createChild(name).setValueType(ValueType.STRING).build();
		cnode.setAttribute("Channel Offset", new Value(offset));
		cnode.setAttribute("Value Mappings", new Value(mapStr));
		
		MultistateComponent mc = new MultistateComponent(this, cnode);
		mc.init();
	}
}
