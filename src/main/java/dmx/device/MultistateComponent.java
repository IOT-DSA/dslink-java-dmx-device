package dmx.device;

import java.util.Map.Entry;
import java.util.Set;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;

public class MultistateComponent extends DmxComponent {
	
	private JsonObject mappings;
	
	MultistateComponent(DmxDevice device, Node node) {
		super(device, node);
		updateMappings();
	}
	
	private void updateMappings() {
		try {
			String mapStr = node.getAttribute("Value Mappings").getString();
			mappings = new JsonObject(mapStr);
			Set<String> enums = mappings.getMap().keySet();
			node.setValueType(ValueType.makeEnum(enums));
		} catch (Exception e) {
			mappings = null;
			node.setValueType(ValueType.STRING);
		}
	}

	@Override
	protected void update() {
		int offset = node.getAttribute("Channel Offset").getNumber().intValue();
		int value = device.conn.channelValues[device.baseAddress + offset];
		if (mappings != null) {
			for (Entry<String, Object> entry: mappings) {
				Object o = entry.getValue();
				if (o instanceof JsonArray) {
					JsonArray range = (JsonArray) o;
					if (range.size() >= 2) {
						Object lower = range.get(0);
						Object upper = range.get(1);
						if (lower instanceof Number && upper instanceof Number && 
								((Number) lower).intValue() <= value && 
								value <= ((Number) upper).intValue()) {
							node.setValue(new Value(entry.getKey()));
						}
					}
				}
			}
		} else {
			node.setValue(new Value(String.valueOf(value)));
		}

	}

	@Override
	protected void makeEditAction() {
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleEdit(event);
			}
		});
		act.addParameter(new Parameter("Channel Offset", ValueType.NUMBER, node.getAttribute("Channel Offset")));
		act.addParameter(new Parameter("Value Mappings", ValueType.STRING, node.getAttribute("Value Mappings")));
		Node anode = node.getChild("edit");
		if (anode == null) node.createChild("edit").setAction(act).build().setSerializable(false);
		else anode.setAction(act);

	}

	@Override
	protected void handleEdit(ActionResult event) {
		int offset = event.getParameter("Channel Offset", ValueType.NUMBER).getNumber().intValue();
		String mapStr = event.getParameter("Value Mappings", ValueType.STRING).getString();
		
		node.setAttribute("Channel Offset", new Value(offset));
		node.setAttribute("Value Mappings", new Value(mapStr));
		
		updateMappings();
		init();

	}

}
