package code.groovy;

import org.junit.jupiter.api.Test;
import se.alipsa.gade.code.groovytab.GroovyTextArea;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroovyTextAreaUnresolvedTypesTest {
  @Test
  void sampleScript_shouldNotFlagWordsInsideStrings() {
    String code = String.join("\n",
        "@GrabConfig(systemClassLoader=true)",
        "@Grab('org.openjdk.nashorn:nashorn-core:15.6')",
        "@Grab('ant:ant-optional:1.5.3-1')",
        "import groovy.ant.AntBuilder",
        "import org.apache.tools.ant.DefaultLogger",
        "import org.apache.tools.ant.Project",
        "",
        "println Class.forName('org.apache.tools.ant.taskdefs.optional.script.ScriptDef')",
        "def ant = new AntBuilder()",
        "def log = new DefaultLogger()",
        "log.setErrorPrintStream(System.err)",
        "log.setOutputPrintStream(System.out)",
        "log.setMessageOutputLevel(Project.MSG_INFO)",
        "ant.project.addBuildListener(log)",
        "",
        "ant.with {",
        "  taskdef(name: 'scriptdef', classname: 'org.apache.tools.ant.taskdefs.optional.script.ScriptDef')",
        "  scriptdef(name:\"scripttest\", language:\"javascript\") {",
        "    attribute(name:\"attr1\")",
        "    element(name:\"fileset\", type:\"fileset\")",
        "    element(name:\"path\", type:\"path\")",
        "    '''",
        "      self.log(\"Hello from script\");",
        "      self.log(\"Attribute attr1 = \" + attributes.get(\"attr1\"));",
        "      self.log(\"First fileset basedir = \"",
        "        + elements.get(\"fileset\").get(0).getDir(project));",
        "    '''",
        "  }",
        "  ",
        "  scripttest(attr1:\"test\") {",
        "    path {",
        "      pathelement(location:\"src\")",
        "    }",
        "   fileset(dir:\"src\")",
        "   fileset(dir:\"main\")",
        "  }",
        "}"
    );

    Set<String> unresolved = GroovyTextArea.getUnresolvedSimpleTypeTokens(code);

    // These appear only in strings => must NOT be flagged.
    assertFalse(unresolved.contains("ScriptDef"), "ScriptDef inside single-quoted string must not be flagged");
    assertFalse(unresolved.contains("Hello"),     "Hello inside triple-quoted string must not be flagged");
    assertFalse(unresolved.contains("Attribute"), "Attribute inside triple-quoted string must not be flagged");
    assertFalse(unresolved.contains("First"),     "First inside triple-quoted string must not be flagged");
  }

  @Test
  void strings_allFlavors_shouldBeIgnored_and_realTypeShouldBeFlagged() {
    String code = String.join("\n",
        "class Foo {}",                           // defined locally
        "def a = ''' Hello Attribute First '''",  // triple single
        "def b = \"\"\" Hello Attribute First \"\"\"", // triple double
        "def c = \"Escaped \\\"Hello\\\" here\"", // normal double
        "def d = 'Attribute and First'",          // normal single
        "def e = /Slashy Hello/",                 // slashy
        "def f = $/Dollar First/$",               // dollar-slashy
        "NoSuchType x"                            // uppercase outside strings -> should be flagged
    );

    Set<String> unresolved = GroovyTextArea.getUnresolvedSimpleTypeTokens(code);

    assertTrue(unresolved.contains("NoSuchType"), "NoSuchType outside strings should be flagged");
    assertFalse(unresolved.contains("Hello"),     "Hello inside strings must not be flagged");
    assertFalse(unresolved.contains("Attribute"), "Attribute inside strings must not be flagged");
    assertFalse(unresolved.contains("First"),     "First inside strings must not be flagged");
    assertFalse(unresolved.contains("Foo"),       "Foo is defined locally and must not be flagged");
  }

  @Test
  void annotations_and_qualified_members_should_not_be_flagged() {
    String code = String.join("\n",
        "@MyAnno",
        "class C {}",
        "Map.Entry e;",             // Entry is after a dot -> must not be counted
        "NoSuchType y"              // should be flagged
    );

    Set<String> unresolved = GroovyTextArea.getUnresolvedSimpleTypeTokens(code);

    assertTrue(unresolved.contains("NoSuchType"), "NoSuchType should be flagged");
    assertFalse(unresolved.contains("MyAnno"),    "Annotation identifiers must not be flagged");
    assertFalse(unresolved.contains("Entry"),     "Identifiers immediately after '.' must not be flagged");
    // We don't assert about Map here; the default-pkg resolution depends on your index.
  }

  @Test
  void javaIoFile_should_not_be_flagged_even_without_explicit_import() {
    String code = String.join("\n",
        "import groovy.ant.AntBuilder",
        "import groovy.grape.Grape",
        "",
        "def project = new AntBuilder()",
        "",
        "project.with {",
        "  def h2 = Grape.instance.resolve(classLoader: this.class.classLoader, [[group:'com.h2database', module:'h2', version:'2.3.232']] as Map[])[0]",
        "  path(id: 'driverPath') {",
        "    pathelement(location: new File(h2))", // <-- File from java.io
        "  }",
        "}"
    );

    var unresolved = GroovyTextArea.getUnresolvedSimpleTypeTokens(code);
    assertFalse(unresolved.contains("File"), "java.io.File is auto-imported; must not be flagged");
  }

  @Test
  void common_default_pkg_types_should_not_be_flagged() {
    String code = String.join("\n",
        "def now = new Date()",             // java.util.Date
        "def list = new ArrayList()",       // java.util.ArrayList
        "def sb = new StringBuilder()",     // java.lang.StringBuilder
        "def f = new File(\"x\")"           // java.io.File
    );
    var unresolved = GroovyTextArea.getUnresolvedSimpleTypeTokens(code);
    assertFalse(unresolved.contains("Date"));
    assertFalse(unresolved.contains("ArrayList"));
    assertFalse(unresolved.contains("StringBuilder"));
    assertFalse(unresolved.contains("File"));
  }
}
