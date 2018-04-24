import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval

def approval_instance = ScriptApproval.get()
def signature  = "${signature}"

def approved_sig = approval_instance.approvedSignatures.find { it == signature }

if (approved_sig) {
  // signature exists so just return
  return ""
}

approval_instance.approveSignature(signature)
approval_instance.save()

return "added signature"
