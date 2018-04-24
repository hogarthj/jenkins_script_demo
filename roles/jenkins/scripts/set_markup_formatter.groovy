def instance = Jenkins.getInstance()

def formatter

def markup_formatter = instance.getMarkupFormatter()

if (markup_formatter instanceof ${formatter_class}) {
  // no need for changes
  return ""
}


switch("${formatter_class}") {

  case 'hudson.markup.EscapedMarkupFormatter':
    formatter = new hudson.markup.EscapedMarkupFormatter();
    break;
  case 'hudson.markup.RawHtmlMarkupFormatter':
    formatter = new hudson.markup.RawHtmlMarkupFormatter(false);
    break;

}

instance.setMarkupFormatter(formatter)

instance.save()

return "changed markup formatter"
