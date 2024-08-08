package utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import se.alipsa.gade.utils.SqlParser;

public class SqlParserTest {

  @Test
  public void testMultipleStatementNoSemicolon() {
    StringBuilder warnings = new StringBuilder();
    String[] statements = SqlParser.split("""
        select * from MyTableA where id = 123456678
        select * from SomeTable where key = '11331607'
        """, warnings);

    System.out.println(warnings);
    System.out.println(statementsString(statements));
  }

  @Test
  public void testMultipleStatementWithSemicolon() {
    StringBuilder warnings = new StringBuilder();
    String[] statements = SqlParser.split("""
        select * from MyTableA where id = 123456678;
        select * from SomeTable where key = '11331607';
        """, warnings);

    assertEquals(0, warnings.length(), warnings.toString());
    assertEquals(2, statements.length, "Expected 2 statments but was: \n" + statementsString(statements));
  }

  @Test
  public void testMultipleStatementNoSemicolonAndPrint() {
    StringBuilder warnings = new StringBuilder();
    String sql = """
        select * from MyTableA A where id = 123456678
          left join MyTableB B on A.id = B.id
        print 'hello world'
        -- select something
        select * from SomeTable where key = '11331607'
        """;
    String[] statements = SqlParser.split(sql, warnings);
    assertEquals(77, warnings.length(), warnings.toString());
    assertEquals(1, statements.length, "Expected 3 statments but was: \n" + statementsString(statements));
    assertEquals(sql, String.join("\n", statements));
  }

  @Test
  public void testMultipleStatementWithSemicolonAndPrint() {
    StringBuilder warnings = new StringBuilder();
    String sql = """
        select * from MyTableA A where id = 123456678
          left join MyTableB B on A.id = B.id;
        print 'hello world';
        select * from SomeTable where key = '11331607';
        """;
    String[] statements = SqlParser.split(sql, warnings);
    assertEquals(77, warnings.length(), warnings.toString());
    assertEquals(1, statements.length, "Expected 1 statement but was: \n" + statementsString(statements));
    assertEquals(sql, String.join("\n", statements));
  }

  @Test
  public void testMultipleStatements() {
    StringBuilder warnings = new StringBuilder();
    String sql = """
        --
        -- Removes all application history of the ssn specified\s
        --
        ---- setup
        print 'setup'
        declare @ssn varchar(20) = '701101-6651'
        -- no need to change anything below
        declare @ssnHash varchar(255) = (dbo.hashSsn(@ssn))
        print 'populating temp tables'
        select @ssn as ssn, @ssnHash as ssnHash into #ssnToRemove;
        select loanApplication_errandId, errandId into #errandIdsToDelete\s
        from LoanApplicationWrapper where mainSsnHashed = (select ssnHash from #ssnToRemove);
        ----- /setup
        print 'here';
        print 'deleting main prospect';
        """;
    String[] statements = SqlParser.split(sql, warnings);

    assertEquals(78, warnings.length(), warnings.toString());
    assertEquals(1, statements.length, "Expected 1 statement but was: \n" + statementsString(statements));
    assertEquals(sql, String.join("\n", statements));
  }

  private static String statementsString(String[] statements) {
    int i = 1;
    StringBuilder sb = new StringBuilder();
    for (String statement : statements) {
      sb.append(i++).append(". ").append(statement).append("\n");
    }
    return sb.substring(0, sb.length() - 1);
  }
}
