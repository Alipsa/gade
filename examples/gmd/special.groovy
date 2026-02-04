import se.alipsa.gmd.core.Gmd

String text = """\
# Some equations
X = &sum;(&radic;(2&pi;) + &#8731;3)
""".stripIndent()
def gmd = new Gmd()
def html = gmd.gmdToHtml(text)
//println html

// This prints well to html
io.view(html)

// But if we wnt to save it as pdf then we must use the full ...Doc method to 
// get the fonts and style right
gmd.htmlToPdf(gmd.gmdToHtmlDoc(text), io.projectFile("/gmd/special.pdf"))