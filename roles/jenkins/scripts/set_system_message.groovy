#!/usr/bin/env groovy

def html_snippet = '''
${system_message}
'''

def instance = Jenkins.getInstance()

if (instance.getSystemMessage() != html_snippet) {

  instance.setSystemMessage(html_snippet)
  instance.save()
  return "changed message"

}
