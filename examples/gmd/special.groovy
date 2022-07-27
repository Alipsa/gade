import se.alipsa.groovy.gmd.Gmd

String text = """\
# Some equations
X = &sum;(&radic;2&pi; + &#8731;3)
""".stripIndent()
def gmd = new Gmd()
def html = """\
    ${Gmd.XHTML_MATHML_DOCTYPE}
    <html>
    <head>
      <meta charset="UTF-8">
    </head>
    <body>
      ${gmd.gmdToHtml(text)}
    </body></html>
    """.stripIndent()
println html
io.view(html)

gmd.htmlToPdf(html, io.projectFile("/gmd/special.pdf"))