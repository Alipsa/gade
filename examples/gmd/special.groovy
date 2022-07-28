import se.alipsa.groovy.gmd.Gmd

String text = """\
# Some equations
X = &sum;(&radic;2&pi; + &#8731;3)
""".stripIndent()
def gmd = new Gmd()
def html = gmd.gmdToHtml(text)
println html
io.view(html)

gmd.htmlToPdf(html, io.projectFile("/gmd/special.pdf"))