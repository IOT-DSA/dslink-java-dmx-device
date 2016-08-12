package dmx.device;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;

public class RgbComponent extends DmxComponent {
	
	RgbComponent(DmxDevice device, Node node) {
		super(device, node);
		this.node.setValueType(ValueType.STRING);
	}

	@Override
	protected void update() {
		int roffset = node.getAttribute("Red Channel Offset").getNumber().intValue();
		int goffset = node.getAttribute("Green Channel Offset").getNumber().intValue();
		int boffset = node.getAttribute("Blue Channel Offset").getNumber().intValue();
		int rvalue = device.conn.channelValues[device.baseAddress + roffset];
		int gvalue = device.conn.channelValues[device.baseAddress + goffset];
		int bvalue = device.conn.channelValues[device.baseAddress + boffset];
		
		StringBuffer sb = new StringBuffer("#");
		sb.append(String.format("%02X", rvalue));
		sb.append(String.format("%02X", gvalue));
		sb.append(String.format("%02X", bvalue));
		node.setValue(new Value(sb.toString()));
	}

	@Override
	protected void makeEditAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleEdit(event);
			}
		});
		act.addParameter(new Parameter("Red Channel Offset", ValueType.NUMBER, node.getAttribute("Red Channel Offset")));
		act.addParameter(new Parameter("Green Channel Offset", ValueType.NUMBER, node.getAttribute("Green Channel Offset")));
		act.addParameter(new Parameter("Blue Channel Offset", ValueType.NUMBER, node.getAttribute("Blue Channel Offset")));
		Node anode = node.getChild("edit");
		if (anode == null) node.createChild("edit").setAction(act).build().setSerializable(false);
		else anode.setAction(act);

	}

	@Override
	protected void handleEdit(ActionResult event) {
		int roffset = event.getParameter("Red Channel Offset", ValueType.NUMBER).getNumber().intValue();
		int goffset = event.getParameter("Green Channel Offset", ValueType.NUMBER).getNumber().intValue();
		int boffset = event.getParameter("Blue Channel Offset", ValueType.NUMBER).getNumber().intValue();
		
		node.setAttribute("Red Channel Offset", new Value(roffset));
		node.setAttribute("Green Channel Offset", new Value(goffset));
		node.setAttribute("Blue Channel Offset", new Value(boffset));
		
		init();
	}

}
