def instance = Jenkins.getInstance()

def jenkins_url ="${rooturl}"

def location_conf = JenkinsLocationConfiguration.get()

if (location_conf.getUrl() != jenkins_url) {

  JenkinsLocationConfiguration.get().setUrl("${rooturl}")
  instance.save()

  return "changed rooturl"
}

