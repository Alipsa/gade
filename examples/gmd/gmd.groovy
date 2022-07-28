import se.alipsa.groovy.gmd.Gmd

text = """\
# Hello

3 + 7 = **<%= 3+7 %>**

```groovy 
def X = Math.sqrt(2* Math.PI) + Math.cbrt(3)
```

X = &sum;(&radic;2&pi; + &#8731;3) = <%=Math.sqrt(2* Math.PI) + Math.cbrt(3)%>

how about _that_?
"""

gmd = new Gmd()

html = gmd.gmdToHtmlDoc(text)
io.view(html , "gmd->html" )

file = io.projectFile("text.gmd")
file.write text
html2 = gmd.gmdToHtmlDoc(file.text)
file.delete()
io.view(html2 , "gmd file ->html" ) 
file = io.projectFile("text.html")
println(html2)
file.write html2
// html2 is not decorated so will not view equation properly
io.view(file)
file.deleteOnExit()
