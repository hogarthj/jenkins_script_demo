changes = ""

def monitor_name = "${monitor_name}"
def enabled = "${enabled}".toBoolean()

def instance = Jenkins.getInstance()
def administrative_monitors = instance.administrativeMonitors

def administrative_monitor = administrative_monitors.find { it.getDisplayName().equals(monitor_name) }

if (administrative_monitor != null) {
  def currentIsEnabled = administrative_monitor.isEnabled()

  if (enabled != currentIsEnabled) {
    administrative_monitor.disable(!enabled)
    instance.save()
    changes = "changed monitor"
  }
}

return changes
