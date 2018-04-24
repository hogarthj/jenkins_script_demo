instance = Jenkins.getInstance()

def node_name = "${node_name}"

node = instance.getNode(node_name)
changes = ""

if (node) {

	instance.removeNode(node)
    verify_node = instance.getNode(node_name)

  if (!verify_node) {
   changes = "node removed"
  } else {
    throw new Exception("Failed to remove node")
  }
}

instance.save()
return changes
