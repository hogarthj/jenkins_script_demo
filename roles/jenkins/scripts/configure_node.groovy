
def node_name = "${node_name}"
def node_execs = "${node_execs}".toInteger()
def node_mode = "${node_mode}".toUpperCase()
def node_labels = "${node_labels}"

def changes = ""

def node = Jenkins.instance.getNode(node_name)

if (node.getComputer().getNumExecutors() != node_execs) {

  node.setNumExecutors(node_execs)
  node.save()

  // notify the computer the node config has changed
  node.getComputer().setNode(node)

  changes = "changed node configuration"

}

if (node.getMode().getName() != node_mode) {

  node.setMode(Node.Mode.valueOf(node_mode))
  node.save()


  changes = "changed node configuration"
}

if (node.getLabelString() != node_labels) {

  node.setLabelString(node_labels)
  node.save()

  changes = "changed node configuration"

}

return changes
