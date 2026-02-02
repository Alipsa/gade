package se.alipsa.gade.code.completion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import se.alipsa.gade.code.completion.sql.SqlCompletionEngine;
import se.alipsa.gade.code.completion.sql.SqlSchemaIntrospector;

class SqlCompletionEngineTest {

  @AfterEach
  void resetSchema() {
    SqlCompletionEngine.setSchemaIntrospector(SqlSchemaIntrospector.NONE);
  }

  @Test
  void suggestsKeywordForPrefix() {
    List<CompletionItem> items = SqlCompletionEngine.complete("sel", "sel", 3);
    assertTrue(items.stream().anyMatch(item ->
        item.kind() == CompletionItem.Kind.KEYWORD && "SELECT".equals(item.completion())));
  }

  @Test
  void suggestsFunctionsInSelectClause() {
    String sql = "select co";
    List<CompletionItem> items = SqlCompletionEngine.complete("co", sql, sql.length());
    assertTrue(items.stream().anyMatch(item ->
        item.kind() == CompletionItem.Kind.FUNCTION &&
            item.completion().toLowerCase(Locale.ROOT).startsWith("count")),
        "Expected COUNT() in function completions");
  }

  @Test
  void suggestsTablesAfterFrom() {
    SqlCompletionEngine.setSchemaIntrospector(new SqlSchemaIntrospector() {
      @Override
      public List<String> tables() {
        return List.of("users", "orders");
      }

      @Override
      public List<String> columns(String table) {
        return List.of();
      }
    });

    String sql = "select * from ";
    List<CompletionItem> items = SqlCompletionEngine.complete("", sql, sql.length());
    assertTrue(items.stream().anyMatch(item ->
        item.kind() == CompletionItem.Kind.TABLE && "users".equals(item.completion())));
  }

  @Test
  void suggestsQualifiedColumnsUsingAlias() {
    SqlCompletionEngine.setSchemaIntrospector(new SqlSchemaIntrospector() {
      @Override
      public List<String> tables() {
        return List.of("users");
      }

      @Override
      public List<String> columns(String table) {
        if ("users".equalsIgnoreCase(table)) {
          return List.of("id", "name", "email");
        }
        return List.of();
      }
    });

    String sql = "select u.id from users u where u.na";
    List<CompletionItem> items = SqlCompletionEngine.complete("na", sql, sql.length());
    assertTrue(items.stream().anyMatch(item ->
        item.kind() == CompletionItem.Kind.COLUMN &&
            "name".equals(item.completion()) &&
            item.renderLabel().contains("u.name")),
        "Expected alias-qualified column suggestion for u.name");
  }
}
