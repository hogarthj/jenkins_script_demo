def instance = Jenkins.getInstance()

def changed = ""

if (instance.getMode().getName() != "EXCLUSIVE") {

  instance.setMode(Node.Mode.EXCLUSIVE)
  instance.save()

  changed = "changed master"

}

if (instance.getNumExecutors() != 0) {

  instance.setNumExecutors(0)
  instance.save()
  changed = "changed master"

}

return changed
