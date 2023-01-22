package se.alipsa.gade.code;

import java.io.File;

public interface TabTextArea {

  /**
   * @return the file this textarea is related to
   * or null if no such relation exists
   */
  File getFile();

  void setFile(File file);

  /**
   * @return the textual content (String) of this text area
   * which resides in a tab.
   */
  String getTextContent();

  /**
   *
   * @return the entire text from the TabTextArea
   */
  String getAllTextContent();

  void replaceContentText(String content, boolean isReadFromFile);

  void replaceContentText(int start, int end, String content);
}
