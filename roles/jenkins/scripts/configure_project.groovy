import com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger
import com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy
import com.cloudbees.jenkins.plugins.bitbucket.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import jenkins.branch.BranchIndexingCause;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.branch.OrganizationFolder
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.trait.SCMTrait;
import org.jenkinsci.plugins.github_branch_source.GitHubSCMNavigator
import org.jenkinsci.plugins.workflow.multibranch.*

def project_config = [:]

project_config['server_url'] = "${server_url}"
project_config['proj_type'] = "${type}"
project_config['proj_name'] = "${project_name}"
project_config['cred_desc'] = "${cred_desc}"
project_config['git_url'] = "${git_url}"
project_config['max_trigger'] = "${max_trigger}"
project_config['daysToKeep'] = "${daysToKeep}"
project_config['numToKeep'] = "${numToKeep}"
project_config['discover_pr_from_origin'] = "${discover_pr_from_origin}".toBoolean()
project_config['discover_pr_from_forks'] = "${discover_pr_from_forks}".toBoolean()
project_config['enableWebhooks'] = "${enableWebhooks}".toBoolean()
project_config['state'] = "${state}"

def changes = ""

def ofs
def project
def projects = []


// no def as we want these globally
instance = Jenkins.getInstance()
global_domain = Domain.global()

def create_project(project_config) {
  def project
  def nav
  def project_cred
  def credentials
  def traits

  credentials = SystemCredentialsProvider.getInstance().getCredentials(global_domain)

  project_cred = credentials.find { it.getDescription().contains(project_config['cred_desc'])  }

  if (project_config['proj_type'] == "mb") {
    project = instance.createProject(WorkflowMultiBranchProject, project_config['proj_name'])
    nav = new BranchSource(new GitSCMSource(null, project_config['git_url'], project_cred.id, "*", "", false),
                           new DefaultBranchPropertyStrategy(new BranchProperty[0])
                          )

    project.getSourcesList().add(nav)

  } else {
    project = instance.createProject(OrganizationFolder, project_config['proj_name'])

    if (project_config['proj_type'] == "bb" ) {
      nav  = new BitbucketSCMNavigator(project_config['proj_name'], project_cred.id, BitbucketSCMSource.DescriptorImpl.SAME)
      traits = new ArrayList<>();

      // This sets both branches without a PR and branches with a PR to build (i.e. all branches found)
      traits.add(new BranchDiscoveryTrait(true, true));

      if (project_config['discover_pr_from_origin']) {
        traits.add(new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
      }

      if (project_config['discover_pr_from_forks']) {
        traits.add(new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                new ForkPullRequestDiscoveryTrait.TrustEveryone())
        );
      }
      traits.add(new PublicRepoPullRequestFilterTrait());
      if (project_config['enableWebhooks']) {
        traits.add(new WebhookRegistrationTrait(WebhookRegistration.ITEM))
      } else {
        traits.add(new WebhookRegistrationTrait(WebhookRegistration.DISABLE))
      }
      nav.setTraits(traits)
    } else if (project_config['proj_type'] == "gh" ) {
      nav = new GitHubSCMNavigator(null, project_config['proj_name'], project_cred.id, project_cred.id)
    }


    project.getNavigators().add(nav)

    if ((project_config['proj_type'] == "bb") && (project_config['server_url'] != "")) {
      project.getNavigators().each { it.setBitbucketServerUrl(project_config['server_url']) }
    }
  }

  project.setOrphanedItemStrategy(new DefaultOrphanedItemStrategy(true, project_config['daysToKeep'], project_config['numToKeep']))

  project.addTrigger(new PeriodicFolderTrigger(project_config['max_trigger']))
  project.scheduleBuild(0, new BranchIndexingCause())
  project.save()

  return "project added"
}

def update_project(project, project_config) {
  def triggers
  def changes = ""
  def current_daysToKeep
  def current_numToKeep

  // these settings are common to all project types

  triggers = project.getTriggers().get(Hudson.instance.getDescriptorOrDie(PeriodicFolderTrigger.class))

  if ( triggers.getInterval() != project_config['max_trigger'] ) {
    project.addTrigger(new PeriodicFolderTrigger(project_config['max_trigger']))
    changes = "project updated"
  }

  // OrphanedItemStrategy
  current_daysToKeep = project.getOrphanedItemStrategy().getDaysToKeep().toString()
  current_numToKeep = project.getOrphanedItemStrategy().getNumToKeep().toString()

  if ( current_daysToKeep != project_config['daysToKeep'] || current_numToKeep != project_config['numToKeep'] ) {
    project.setOrphanedItemStrategy(new DefaultOrphanedItemStrategy(true, project_config['daysToKeep'], project_config['numToKeep']))
    changes = "project updated"
  }

  // handle the bb specific options
  if (project_config['proj_type'] == "bb") {

    def currentdiscover_pr_from_origin = false
    def currentdiscover_pr_from_forks = false
    def currentEnableWebhooks

    nav = project.getNavigators().get(BitbucketSCMNavigator.class)
    def currentTraits = nav.getTraits()

    for (SCMTrait<? extends SCMTrait<?>> t : currentTraits) {
      if (t instanceof OriginPullRequestDiscoveryTrait) {
        currentdiscover_pr_from_origin = true
      } else if (t instanceof ForkPullRequestDiscoveryTrait) {
        currentdiscover_pr_from_forks = true
      } else if (t instanceof WebhookRegistrationTrait) {
         currentEnableWebhooks = (t.getMode() != WebhookRegistration.DISABLE)
      }
    }

    if ((project_config['discover_pr_from_origin'] != currentdiscover_pr_from_origin) || (project_config['discover_pr_from_forks'] != currentdiscover_pr_from_forks) || (project_config['enableWebhooks'] != currentEnableWebhooks)) {

      def traits = new ArrayList<>()

      // populate the traits with whatever is currently set, apart from the ones we want to change
      for (SCMTrait<? extends SCMTrait<?>> t : currentTraits) {
        if ((t instanceof OriginPullRequestDiscoveryTrait) || (t instanceof ForkPullRequestDiscoveryTrait) || (t instanceof WebhookRegistrationTrait)) {
          continue
        } else {
          traits.add(t)
        }
      }

      if (project_config['discover_pr_from_origin']) {
        traits.add(new OriginPullRequestDiscoveryTrait(EnumSet.of(ChangeRequestCheckoutStrategy.HEAD)));
      }

      if (project_config['discover_pr_from_forks']) {
        traits.add(new ForkPullRequestDiscoveryTrait(
                EnumSet.of(ChangeRequestCheckoutStrategy.HEAD),
                new ForkPullRequestDiscoveryTrait.TrustEveryone())
        );
      }

      // we skipped it on any change detected, but we need this set one way or another ... so ensure it is set right
      traits.add(new WebhookRegistrationTrait(project_config['enableWebhooks'] ? WebhookRegistration.ITEM : WebhookRegistration.DISABLE))

      nav.setTraits(traits)
      changes = "project updated"
    }

  }

  project.save()
  return changes

}

def remove_project(project, project_config) {
  def projects

  instance.remove(project)
  instance.save()

  // verify it has gone
  def ofs = instance.getAllItems(OrganizationFolder)

  if (project_config['proj_type'] == "bb" )  {
    projects = ofs.findAll { it.getNavigators().find { it instanceof BitbucketSCMNavigator  }  }
  } else if (project_config['proj_type'] == "gh") {
    projects = ofs.findAll { it.getNavigators().find { it instanceof GitHubSCMNavigator  }  }
  } else if (project_config['proj_type'] == "mb") {
    projects = instance.getAllItems(WorkflowMultiBranchProject)
  }

  project = projects.find { it.name == project_config['proj_name']  }
  if (project) {
    throw new Exception("Failed to remove project")
  } else {
    return "project removed"
  }

}

ofs = instance.getAllItems(OrganizationFolder)

if (project_config['proj_type'] == "bb" )  {
  projects = ofs.findAll { it.getNavigators().find { it instanceof BitbucketSCMNavigator  }  }
} else if (project_config['proj_type'] == "gh") {
  projects = ofs.findAll { it.getNavigators().find { it instanceof GitHubSCMNavigator  }  }
} else if (project_config['proj_type'] == "mb") {
  projects = instance.getAllItems(WorkflowMultiBranchProject)
}

project = projects.find { it.name == project_config['proj_name']  }

// add or update the project when it needs to exist
if (project_config['state'] == 'present') {
  if (project) {
    // project exists so update it
    changes = update_project(project, project_config)
  } else {
    // project doesn't exist so create it
    changes = create_project(project_config)
  }
} else if (project_config['state'] == 'absent') {
    // if it needs to be removed but it exists then do so
    if (project) {
      changes = remove_project(project, project_config)
    }

} else {
    throw new Exception("state must be either present or absent")
}

return changes
