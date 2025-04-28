import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;

def classLoader = new GroovyClassLoader(null as ClassLoader)
def engine = new GroovyScriptEngineImpl(classLoader)

engine.eval("""
  def numbers = [0, 1, 2]
  assert [0, 1, 2] == GQ {
      from n in numbers
      select n
  }.toList()
""")