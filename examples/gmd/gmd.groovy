import se.alipsa.groovy.gmd.Gmd

text = """\
# Hello

3 + 7 = **<%= 3+7 %>**

how about _that_?

X = &sum;(&radic;2&pi; + &#8731;3)
"""

gmd = new Gmd()

html = gmd.gmdToHtml(text)
io.view(html , "gmd->html" )

file = new File(io.scriptDir(), "test.gmd")
html2 = gmd.gmdToHtml(file.text)
io.view(html2 , "gmd file ->html" )
