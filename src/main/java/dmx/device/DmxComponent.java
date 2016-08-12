package dmx.device;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.util.handler.Handler;

public abstract class DmxComponent {
	
	protected DmxDevice device;
	protected Node node;
	
	DmxComponent(DmxDevice device, Node node) {
		this.device = device;
		this.node = node;
		this.device.components.add(this);
	}
	
	void init() {
		makeEditAction();
		makeRemoveAction();
		update();
	}
	
	void restoreLastSession() {
		makeEditAction();
		makeRemoveAction();
	}
	
	protected abstract void update();
	
	protected abstract void makeEditAction();
	
	protected abstract void handleEdit(ActionResult event);
	
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
		device.components.remove(this);
		node.clearChildren();
		node.getParent().removeChild(node);
	}

}
