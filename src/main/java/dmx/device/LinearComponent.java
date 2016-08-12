package dmx.device;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;

public class LinearComponent extends DmxComponent {
	
	LinearComponent(DmxDevice device, Node node) {
		super(device, node);
		this.node.setValueType(ValueType.NUMBER);
	}

	@Override
	protected void update() {
		int offset = node.getAttribute("Channel Offset").getNumber().intValue();
		int value = device.conn.channelValues[device.baseAddress + offset];
		node.setValue(new Value(value));
	}

	@Override
	protected void makeEditAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleEdit(event);
			}
		});
		act.addParameter(new Parameter("Channel Offset", ValueType.NUMBER, node.getAttribute("Channel Offset")));
		Node anode = node.getChild("edit");
		if (anode == null) node.createChild("edit").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
		
	}

	@Override
	protected void handleEdit(ActionResult event) {
		int offset = event.getParameter("Channel Offset", ValueType.NUMBER).getNumber().intValue();
		
		node.setAttribute("Channel Offset", new Value(offset));
		
		init();
		
	}
	
	

}
