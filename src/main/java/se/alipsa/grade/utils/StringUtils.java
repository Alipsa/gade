package se.alipsa.grade.utils;

import org.apache.logging.log4j.message.FormattedMessageFactory;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
  private static final Pattern LEADING_SPACES = Pattern.compile("^\\s+");

  private static final FormattedMessageFactory factory = new FormattedMessageFactory();

  private static NumberFormat numberFormat;

  static {
    // TODO allow user to change default local in global options (Locale.setDefault(theLocale))
    // For now you can change the default locale in env.cmd/env.sh e.g.
    // SET JAVA_OPTS=-Duser.country=SE -Duser.language=sv
    numberFormat= DecimalFormat.getInstance(Locale.getDefault());
    numberFormat.setGroupingUsed(true);
  }
  public static String format(String text, Object... args) {
    //return org.slf4j.helpers.MessageFormatter.arrayFormat(text, args).getMessage();
    return factory.newMessage(text, args).getFormattedMessage();
  }

  public static String formatNumber(Number number) {
    return numberFormat.format(number);
  }

  public static String fixedLengthString(String string, int length) {
    return String.format("%1$"+length+ "s", string);
  }

  public static String maxLengthString(String string, int length) {
    if (string.length() <= length) {
      return string;
    } else {
      return string.substring(0, length -3) + "...";
    }
  }

  public static String getLeadingSpaces(String str) {
    Matcher m = LEADING_SPACES.matcher(str);
    if(m.find()){
      return m.group(0);
    }
    return "";
  }

  public static String underLine(String name, char character) {
    return name + "\n" + String.valueOf(character).repeat(name.length()) + "\n";
  }

  public static boolean isBlank(String str) {
    if (str == null) {
      return true;
    }
    return str.isBlank();
  }
}
